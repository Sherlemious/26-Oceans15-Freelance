package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.AppliedPromoCodeDTO;
import com.team26.freelance.wallet.dto.ContractDataProjection;
import com.team26.freelance.wallet.dto.FreelancerPayoutSummaryDTO;
import com.team26.freelance.wallet.dto.PayoutDetailsDTO;
import com.team26.freelance.wallet.dto.PayoutResponseDTO;
import com.team26.freelance.wallet.dto.PayoutReversalResultDTO;
import com.team26.freelance.wallet.dto.ProcessContractPayoutRequest;
import com.team26.freelance.wallet.dto.PromoCodeUsageDTO;
import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.DiscountType;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutAuditEventType;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutPromo;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.repository.PayoutPromoRepository;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import com.team26.freelance.wallet.strategy.RefundResult;
import com.team26.freelance.wallet.strategy.RefundStrategy;
import com.team26.freelance.wallet.strategy.RefundStrategySelector;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayoutService {

  private final PayoutRepository payoutRepository;
  private final PromoCodeRepository promoCodeRepository;
  private final PayoutPromoRepository payoutPromoRepository;
  private final RefundStrategySelector refundStrategySelector;
  private final PayoutAuditService payoutAuditService;

  public PayoutService(PayoutRepository payoutRepository,
                       PromoCodeRepository promoCodeRepository,
                       PayoutPromoRepository payoutPromoRepository,
                       RefundStrategySelector refundStrategySelector,
                       PayoutAuditService payoutAuditService) {
    this.payoutRepository = payoutRepository;
    this.promoCodeRepository = promoCodeRepository;
    this.payoutPromoRepository = payoutPromoRepository;
    this.refundStrategySelector = refundStrategySelector;
    this.payoutAuditService = payoutAuditService;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#payoutId"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  @Transactional
  public PayoutResponseDTO applyPromoToPayout(Long payoutId, Long promoCodeId) {
    Payout payout = payoutRepository.findByIdWithPromos(payoutId).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));

    PromoCode promoCode =
        promoCodeRepository.findByIdForUpdate(promoCodeId)
            .orElseThrow(()
                             -> new ResponseStatusException(
                                 HttpStatus.NOT_FOUND, "Promo code not found"));

    if (payout.getStatus() != PayoutStatus.PENDING) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Promo code can only be applied to pending payouts");
    }

    if (!Boolean.TRUE.equals(promoCode.getActive())) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Promo code is inactive");
    }

    LocalDateTime now = LocalDateTime.now();
    if (!promoCode.getExpiryDate().isAfter(now)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Promo code is expired");
    }

    int currentUses =
        promoCode.getCurrentUses() == null ? 0 : promoCode.getCurrentUses();
    if (currentUses >= promoCode.getMaxUses()) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Promo code max uses reached");
    }

    if (payoutPromoRepository.existsByPayout_IdAndPromoCode_Id(payoutId,
                                                               promoCodeId)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST,
          "Promo code is already applied to this payout");
    }

    PayoutPromo payoutPromo = new PayoutPromo();
    payoutPromo.setPayout(payout);
    payoutPromo.setPromoCode(promoCode);
    payoutPromo.setDiscountApplied(calculateDiscountApplied(payout, promoCode));
    payoutPromo.setAppliedAt(now);
    payoutPromoRepository.save(payoutPromo);
    payout.getPayoutPromos().add(payoutPromo);

    promoCode.setCurrentUses(currentUses + 1);
    promoCodeRepository.save(promoCode);

    return new PayoutResponseDTO(payout);
  }

  private double calculateDiscountApplied(Payout payout, PromoCode promoCode) {
    BigDecimal amount = BigDecimal.valueOf(payout.getAmount());
    BigDecimal discountValue = BigDecimal.valueOf(promoCode.getDiscountValue());
    BigDecimal discountApplied;

    if (promoCode.getDiscountType() == DiscountType.PERCENTAGE) {
      discountApplied =
          amount.multiply(discountValue).divide(BigDecimal.valueOf(100));
    } else {
      discountApplied = discountValue;
    }

    return discountApplied.min(amount).doubleValue();
  }

  public List<Payout> getAllPayouts() { return payoutRepository.findAll(); }

  @Cacheable(cacheNames = "wallet-service::payout", key = "#id")
  public Payout getPayoutById(Long id) {
    return payoutRepository.findById(id).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  @Transactional
  public Payout createPayout(Payout payout) {
    Payout saved = payoutRepository.save(payout);
    payoutAuditService.recordLifecycleEvent(saved, PayoutAuditEventType.CREATED, "Payout created");
    return saved;
  }


  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::payout", allEntries = true)
  })
  @Transactional
  public Payout processContractPayout(Long contractId,
                                      ProcessContractPayoutRequest request) {
    List<ContractDataProjection> contractRows = payoutRepository.findContractDataById(contractId);
    if (contractRows.isEmpty()) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Contract not found");
    }
    ContractDataProjection contractData = contractRows.get(0);
    String contractStatus = contractData.getContractStatus();
    Double agreedAmount = contractData.getAgreedAmount();
    Long freelancerId = contractData.getFreelancerId();

    if (agreedAmount == null || freelancerId == null) {
      throw new ResponseStatusException(HttpStatus.CONFLICT,
                                        "Contract data is incomplete for payout processing");
    }

    if (!"COMPLETED".equals(contractStatus)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Contract must be COMPLETED");
    }

    if (payoutRepository.existsByContractIdAndStatus(contractId,
                                                     PayoutStatus.COMPLETED)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "already paid");
    }

    Payout pendingPayout =
        payoutRepository
            .findFirstByContractIdAndStatusOrderByCreatedAtAsc(
                contractId, PayoutStatus.PENDING)
            .orElseGet(() -> {
              Payout p = new Payout();
              p.setContractId(contractId);
              p.setFreelancerId(freelancerId);
              p.setAmount(agreedAmount);
              p.setMethod(PayoutMethod.BANK_TRANSFER);
              p.setStatus(PayoutStatus.PENDING);
              p.setTransactionDetails(new HashMap<>());
              return p;
            });

    PayoutMethod method = normalizePayoutMethod(request != null ? request.getMethod() : null);
    String accountLastFour = request != null ? request.getAccountLastFour() : null;

    if (accountLastFour != null && !accountLastFour.isBlank() &&
        !accountLastFour.matches("\\d{4}")) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "accountLastFour must be exactly 4 digits");
    }

    Map<String, Object> transactionDetails =
        pendingPayout.getTransactionDetails();
    if (transactionDetails == null) {
      transactionDetails = new HashMap<>();
    }
    if (method != null) {
      transactionDetails.put("method", method.name());
      pendingPayout.setMethod(method);
    }
    if (accountLastFour != null && !accountLastFour.isBlank()) {
      transactionDetails.put("accountLastFour", accountLastFour);
    }

    if (pendingPayout.getAmount() != null) {
      double platformFee = BigDecimal.valueOf(pendingPayout.getAmount())
          .multiply(BigDecimal.valueOf(0.10))
          .setScale(2, java.math.RoundingMode.HALF_UP)
          .doubleValue();
      transactionDetails.put("platformFee", platformFee);
    }

    pendingPayout.setStatus(PayoutStatus.COMPLETED);
    pendingPayout.setTransactionDetails(transactionDetails);

    Payout saved = payoutRepository.save(pendingPayout);
    payoutAuditService.recordLifecycleEvent(saved, PayoutAuditEventType.COMPLETED, "Contract payout completed");
    return saved;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  @Transactional
  public Payout updatePayout(Long id, Payout updated) {
    Payout existing = getPayoutById(id);
    PayoutStatus previousStatus = existing.getStatus();
    existing.setContractId(updated.getContractId());
    existing.setFreelancerId(updated.getFreelancerId());
    existing.setAmount(updated.getAmount());
    existing.setMethod(updated.getMethod());
    existing.setStatus(updated.getStatus());
    existing.setTransactionDetails(updated.getTransactionDetails());
    Payout saved = payoutRepository.save(existing);
    recordStatusTransition(saved, previousStatus, saved.getStatus(), "Payout updated");
    return saved;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  public void deletePayout(Long id) {
    getPayoutById(id);
    payoutRepository.deleteById(id);
  }

  @Cacheable(
          cacheNames = "wallet-service::S5-F1",
          key = "T(java.util.Objects).hash(#status, #startDate, #endDate)"
  )
  public List<Payout> searchByStatusAndDateRange(String status,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
    LocalDateTime start = startDate != null
        ? startDate.atStartOfDay()
        : LocalDateTime.of(1970, 1, 1, 0, 0);
    LocalDateTime end = endDate != null
        ? endDate.atTime(23, 59, 59)
        : LocalDateTime.of(2100, 12, 31, 23, 59, 59);
    return payoutRepository.searchByStatusAndDateRange(status, start, end);
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  @Transactional
  public Payout processRefund(Long id, String reason) {
    Payout payout = payoutRepository.findById(id).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));
    if (payout.getStatus() != PayoutStatus.COMPLETED) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "Only COMPLETED payouts can be refunded");
    }
    payout.setStatus(PayoutStatus.REFUNDED);
    Map<String, Object> transactionDetails = payout.getTransactionDetails();
    if (transactionDetails == null) {
      transactionDetails = new HashMap<>();
    }
    transactionDetails.put("refundReason", reason);
    transactionDetails.put("refundedAt", LocalDateTime.now().toString());
    payout.setTransactionDetails(transactionDetails);
    Payout saved = payoutRepository.save(payout);
    payoutAuditService.recordLifecycleEvent(saved, PayoutAuditEventType.REFUNDED, reason);
    return saved;
  }

  private PayoutMethod normalizePayoutMethod(PayoutMethod method) {
    if (method == PayoutMethod.BANK) {
      return PayoutMethod.BANK_TRANSFER;
    }
    return method;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  @Transactional
  public Payout retryFailedPayout(Long id) {
    Payout payout = payoutRepository.findById(id).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));

    if (payout.getStatus() != PayoutStatus.FAILED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Only FAILED payouts can be retried");
    }

    payout.setStatus(PayoutStatus.COMPLETED);

    Map<String, Object> transactionDetails = payout.getTransactionDetails();
    if (transactionDetails == null) {
      transactionDetails = new HashMap<>();
    }

    int retryAttempt = 0;
    Object retryValue = transactionDetails.get("retryAttempt");

    if (retryValue instanceof Number number) {
      retryAttempt = number.intValue();
    } else if (retryValue instanceof String str) {
      try {
        retryAttempt = Integer.parseInt(str);
      } catch (NumberFormatException ignored) {
        retryAttempt = 0;
      }
    }

    transactionDetails.put("retryAttempt", retryAttempt + 1);
    transactionDetails.put("gatewayResponse", "approved");

    payout.setTransactionDetails(transactionDetails);

    Payout saved = payoutRepository.save(payout);
    payoutAuditService.recordLifecycleEvent(saved, PayoutAuditEventType.COMPLETED, "Failed payout retried successfully");
    return saved;
  }
  @Cacheable(cacheNames = "wallet-service::S5-F8", key = "#payoutId")
  public PayoutDetailsDTO getPayoutDetails(Long payoutId) {
    Payout payout = payoutRepository.findByIdWithPromos(payoutId).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));

    List<AppliedPromoCodeDTO> appliedPromoCodes = new ArrayList<>();
    double totalDiscount = 0.0;

    for (PayoutPromo payoutPromo : payout.getPayoutPromos()) {
      AppliedPromoCodeDTO promoDTO = new AppliedPromoCodeDTO(
          payoutPromo.getPromoCode().getCode(),
          payoutPromo.getPromoCode().getDiscountType().name(),
          payoutPromo.getDiscountApplied(), payoutPromo.getAppliedAt());

      appliedPromoCodes.add(promoDTO);
      totalDiscount += payoutPromo.getDiscountApplied();
    }

    PayoutDetailsDTO dto = new PayoutDetailsDTO();
    dto.setPayoutId(payout.getId());
    dto.setContractId(payout.getContractId());
    dto.setFreelancerId(payout.getFreelancerId());
    dto.setOriginalAmount(payout.getAmount());
    dto.setMethod(payout.getMethod().name());
    dto.setStatus(payout.getStatus().name());
    dto.setTransactionDetails(payout.getTransactionDetails());
    dto.setAppliedPromoCodes(appliedPromoCodes);
    dto.setTotalDiscount(totalDiscount);
    dto.setFinalAmount(payout.getAmount() - totalDiscount);

    return dto;
  }

  @Cacheable(cacheNames = "wallet-service::S5-F3", key = "#freelancerId")
  public FreelancerPayoutSummaryDTO getFreelancerPayoutSummary(Long freelancerId) {
    List<Object[]> rows = payoutRepository.getPayoutSummaryByFreelancer(freelancerId);
    Map<String, Double> methodBreakdown = new LinkedHashMap<>();
    long totalPayouts = 0;
    double totalAmount = 0.0;
    for (Object[] row : rows) {
      String method = (String) row[0];
      long count = ((Number) row[1]).longValue();
      double sum = ((Number) row[2]).doubleValue();
      methodBreakdown.put(method, sum);
      totalPayouts += count;
      totalAmount += sum;
    }
    return new FreelancerPayoutSummaryDTO(freelancerId, totalPayouts, totalAmount, methodBreakdown);
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true)
  })
  @Transactional
  public PayoutReversalResultDTO reversePayout(Long id, RefundRequest request) {
    Payout payout = payoutRepository.findByIdWithPromos(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));

    if (payout.getStatus() != PayoutStatus.COMPLETED) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
          "Only COMPLETED payouts can be reversed");
    }

    RefundStrategy strategy = refundStrategySelector.select(payout, request);
    RefundResult result = strategy.calculateRefund(payout, request);

    if (!result.isApproved()) {
      payoutAuditService.recordRefundResult(payout, false, result.getAmount(), result.getStrategyName(), result.getReasonCode());
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getReasonCode());
    }

    payout.setStatus(PayoutStatus.REFUNDED);
    Map<String, Object> details = payout.getTransactionDetails();
    if (details == null) {
      details = new HashMap<>();
    }
    details.put("refundAmount", result.getAmount());
    details.put("reversalScope", request.getReversalScope());
    details.put("refundReason", request.getReason());
    details.put("refundedAt", LocalDateTime.now().toString());
    payout.setTransactionDetails(details);
    payoutRepository.save(payout);

    payoutAuditService.recordRefundResult(payout, true, result.getAmount(), result.getStrategyName(), result.getReasonCode());

    return new PayoutReversalResultDTO(payout, result);
  }

  private void recordStatusTransition(Payout payout, PayoutStatus previousStatus,
                                      PayoutStatus currentStatus, String reason) {
    if (currentStatus == null || currentStatus == previousStatus) {
      return;
    }
    if (currentStatus == PayoutStatus.COMPLETED) {
      payoutAuditService.recordLifecycleEvent(payout, PayoutAuditEventType.COMPLETED, reason);
    } else if (currentStatus == PayoutStatus.FAILED) {
      payoutAuditService.recordLifecycleEvent(payout, PayoutAuditEventType.FAILED, reason);
    } else if (currentStatus == PayoutStatus.REFUNDED) {
      payoutAuditService.recordLifecycleEvent(payout, PayoutAuditEventType.REFUNDED, reason);
    }
  }


  @Cacheable(cacheNames = "wallet-service::S5-F9", key = "#limit")
  public List<PromoCodeUsageDTO> getTopUsedPromoCodes(int limit) {
    if (limit <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Limit must be positive");
    }

    List<Object[]> rows = promoCodeRepository.findTopUsedPromoCodes(limit);
    List<PromoCodeUsageDTO> result = new ArrayList<>();
    LocalDateTime now = LocalDateTime.now();

    for (Object[] row : rows) {
      PromoCodeUsageDTO dto = new PromoCodeUsageDTO();

      dto.setPromoCodeId(((Number)row[0]).longValue());
      dto.setCode((String)row[1]);
      dto.setDiscountType((String)row[2]);
      dto.setDiscountValue(((Number)row[3]).doubleValue());
      dto.setTimesUsed(((Number)row[4]).intValue());
      dto.setTotalDiscountGiven(
          row[5] == null ? 0.0 : ((Number)row[5]).doubleValue());
      dto.setActive((Boolean)row[6]);

      LocalDateTime expiryDate;
      if (row[7] instanceof LocalDateTime localDateTime) {
        expiryDate = localDateTime;
      } else if (row[7] instanceof Timestamp timestamp) {
        expiryDate = timestamp.toLocalDateTime();
      } else {
        throw new ResponseStatusException(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Unexpected expiry date type returned from database");
      }

      dto.setExpired(expiryDate.isBefore(now));

      result.add(dto);
    }

    return result;
  }
}
