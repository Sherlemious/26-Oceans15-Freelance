package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.adapter.FreelancerPayoutSummaryObjectArrayAdapter;
import com.team26.freelance.wallet.adapter.PromoCodeUsageObjectArrayAdapter;
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
import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.common.event.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
import org.springframework.context.ApplicationEventPublisher;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayoutService {

  private static final Logger log = LoggerFactory.getLogger(PayoutService.class);
  private static final long SLOW_OPERATION_THRESHOLD_MS = 1000L;

  private final PayoutRepository payoutRepository;
  private final PromoCodeRepository promoCodeRepository;
  private final PayoutPromoRepository payoutPromoRepository;
  private final ApplicationEventPublisher eventPublisher;
  private final PlatformFeeAnalyticsService platformFeeAnalyticsService;
  private final PayoutAuditEventRepository payoutAuditEventRepository;
  private final RefundStrategySelector refundStrategySelector;
  private final PayoutAuditService payoutAuditService;
  private final WalletReadClientService walletReadClientService;
  private final FreelancerPayoutSummaryObjectArrayAdapter freelancerPayoutSummaryObjectArrayAdapter;
  private final PromoCodeUsageObjectArrayAdapter promoCodeUsageObjectArrayAdapter;

  public PayoutService(PayoutRepository payoutRepository,
                       PromoCodeRepository promoCodeRepository,
                       PayoutPromoRepository payoutPromoRepository,
                       PayoutAuditService payoutAuditService,
                       ApplicationEventPublisher eventPublisher,
                       PlatformFeeAnalyticsService platformFeeAnalyticsService,
                       PayoutAuditEventRepository payoutAuditEventRepository,
                       RefundStrategySelector refundStrategySelector,
                       WalletReadClientService walletReadClientService,
                       FreelancerPayoutSummaryObjectArrayAdapter freelancerPayoutSummaryObjectArrayAdapter,
                       PromoCodeUsageObjectArrayAdapter promoCodeUsageObjectArrayAdapter
                       ) {
    this.payoutRepository = payoutRepository;
    this.promoCodeRepository = promoCodeRepository;
    this.payoutPromoRepository = payoutPromoRepository;
    this.eventPublisher = eventPublisher;
    this.platformFeeAnalyticsService = platformFeeAnalyticsService;
    this.payoutAuditEventRepository = payoutAuditEventRepository;
    this.refundStrategySelector = refundStrategySelector;
    this.payoutAuditService = payoutAuditService;
    this.walletReadClientService = walletReadClientService;
    this.promoCodeUsageObjectArrayAdapter=promoCodeUsageObjectArrayAdapter;
    this.freelancerPayoutSummaryObjectArrayAdapter=freelancerPayoutSummaryObjectArrayAdapter;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#payoutId"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
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

    Map<String, Object> auditDetails = new LinkedHashMap<>();
    auditDetails.put("promoCodeId", promoCode.getId());
    auditDetails.put("code", promoCode.getCode());
    auditDetails.put("discountApplied", payoutPromo.getDiscountApplied());
    auditDetails.put("payoutPromoId", payoutPromo.getId());
    payoutAuditService.recordPayoutEvent(payout, PayoutAuditService.PROMO_APPLIED, auditDetails);

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
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
  })
  @Transactional
  public Payout createPayout(Payout payout) {
    Payout saved = payoutRepository.save(payout);
    payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.PAYOUT_CREATED, Map.of("reason", "Payout created"));
    recordLifecycleStatusChange(saved, null, saved.getStatus(), "Payout created with lifecycle status");
    return saved;
  }


  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::payout", allEntries = true)
  })
  @Transactional
  public Payout processContractPayout(Long contractId,
                                      ProcessContractPayoutRequest request,
                                      boolean simulateFailure) {
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
    boolean createdNewPayout = pendingPayout.getId() == null;

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

    if (simulateFailure) {
      transactionDetails.put("simulateFailure", true);
      transactionDetails.put("gatewayResponse", "rejected");
      transactionDetails.put("failureReason", "simulated gateway failure");
      pendingPayout.setStatus(PayoutStatus.FAILED);
    } else {
      transactionDetails.put("gatewayResponse", "approved");
      pendingPayout.setStatus(PayoutStatus.COMPLETED);
    }
    pendingPayout.setTransactionDetails(transactionDetails);

    Payout saved = payoutRepository.save(pendingPayout);
    if (createdNewPayout && !simulateFailure) {
      Map<String, Object> createdDetails = new LinkedHashMap<>();
      createdDetails.put("contractId", contractId);
      createdDetails.put("freelancerId", freelancerId);
      createdDetails.put("reason", "Payout row inserted for contract payout");
      payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.CREATED, createdDetails);
    }

    Map<String, Object> completionDetails = new LinkedHashMap<>();
    completionDetails.put("contractId", contractId);
    completionDetails.put("accountLastFour", accountLastFour);
    completionDetails.put("platformFee", transactionDetails.get("platformFee"));
    completionDetails.put("simulateFailure", simulateFailure);
    if (simulateFailure) {
      completionDetails.put("failureReason", transactionDetails.get("failureReason"));
      payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.FAILED, completionDetails);
    } else {
      completionDetails.put("reason", "Contract payout completed");
      payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.COMPLETED, completionDetails);
    }
    return saved;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
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
    payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.PAYOUT_UPDATED, Map.of("reason", "Payout updated"));
    recordLifecycleStatusChange(saved, previousStatus, saved.getStatus(), "Payout status changed through update");
    return saved;
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
  })
  @Transactional
  public void deletePayout(Long id) {
    Payout payout = payoutRepository.findById(id).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));
    payoutRepository.deleteById(id);
    payoutAuditService.recordPayoutEvent(payout, PayoutAuditService.PAYOUT_DELETED, Map.of("reason", "Payout deleted"));
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
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
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
    Map<String, Object> auditDetails = new LinkedHashMap<>();
    auditDetails.put("reason", reason);
    auditDetails.put("refundedAt", transactionDetails.get("refundedAt"));
    payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.REFUNDED, auditDetails);
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
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
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
    Map<String, Object> auditDetails = new LinkedHashMap<>();
    auditDetails.put("retryAttempt", retryAttempt + 1);
    auditDetails.put("gatewayResponse", transactionDetails.get("gatewayResponse"));
    auditDetails.put("previousStatus", PayoutStatus.FAILED.name());
    auditDetails.put("newStatus", PayoutStatus.COMPLETED.name());
    payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.RETRY_ATTEMPTED, auditDetails);
    payoutAuditService.recordPayoutEvent(saved, PayoutAuditService.COMPLETED, auditDetails);
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

    PayoutDetailsDTO dto = PayoutDetailsDTO.builder()
            .payoutId(payout.getId())
            .contractId(payout.getContractId())
            .freelancerId(payout.getFreelancerId())
            .originalAmount(payout.getAmount())
            .method(payout.getMethod().name())
            .status(payout.getStatus().name())
            .transactionDetails(payout.getTransactionDetails())
            .appliedPromoCodes(appliedPromoCodes)
            .totalDiscount(totalDiscount)
            .finalAmount(payout.getAmount() - totalDiscount)
            .build();

    return dto;
  }

  @Cacheable(cacheNames = "wallet-service::S5-F3", key = "#freelancerId")
  public FreelancerPayoutSummaryDTO getFreelancerPayoutSummary(Long freelancerId) {
    try {
      walletReadClientService.getUser(freelancerId);
    } catch (ResponseStatusException ex) {
      log.warn(
          "Unable to validate freelancer {} before payout summary lookup: status={}, reason={}",
          freelancerId,
          ex.getStatusCode(),
          ex.getReason());
      throw ex;
    } catch (RuntimeException ex) {
      log.warn(
          "Unexpected failure validating freelancer {} before payout summary lookup",
          freelancerId,
          ex);
      throw ex;
    }

    List<Object[]> rows = payoutRepository.getPayoutSummaryByFreelancer(freelancerId);
    if (rows.isEmpty()) {
      log.info("No completed payouts found for freelancer {}", freelancerId);
      throw new ResponseStatusException(
          HttpStatus.NOT_FOUND, "Freelancer not found or has no payouts");
    }

    Map<String, Double> methodBreakdown = new LinkedHashMap<>();
    long totalPayouts = 0;
    double totalAmount = 0.0;

    for (Object[] row : rows) {
      FreelancerPayoutSummaryDTO adapted = freelancerPayoutSummaryObjectArrayAdapter.adapt(row);

      totalPayouts += adapted.getTotalPayouts();
      totalAmount += adapted.getTotalAmount();

      if (adapted.getMethodBreakdown() != null) {
        methodBreakdown.putAll(adapted.getMethodBreakdown());
      }
    }

    return FreelancerPayoutSummaryDTO.builder()
            .freelancerId(freelancerId)
            .totalPayouts(totalPayouts)
            .totalAmount(totalAmount)
            .methodBreakdown(methodBreakdown)
            .build();
  }

  @Cacheable(
          cacheNames = "wallet-service::S5-READ-DB-total",
          key = "#freelancerId + ':' + #startDate + ':' + #endDate"
  )
  public BigDecimal getCompletedPayoutTotalByFreelancer(Long freelancerId,
                                                        LocalDate startDate,
                                                        LocalDate endDate) {
    if (startDate.isAfter(endDate)) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "startDate must be before or equal to endDate");
    }

    long startedAt = System.currentTimeMillis();
    MDC.put("userId", freelancerId.toString());
    try {
      LocalDateTime start = startDate.atStartOfDay();
      LocalDateTime endExclusive = endDate.plusDays(1).atStartOfDay();
      Double total = payoutRepository.getCompletedPayoutTotalByFreelancer(
              freelancerId, PayoutStatus.COMPLETED, start, endExclusive);
      return total == null ? BigDecimal.valueOf(0.0) : BigDecimal.valueOf(total);
    } finally {
      long elapsedMs = System.currentTimeMillis() - startedAt;
      if (elapsedMs > SLOW_OPERATION_THRESHOLD_MS) {
        log.warn("Slow completed-payout-total lookup took {}ms", elapsedMs);
      }
      MDC.remove("userId");
    }
  }

  @Caching(evict = {
          @CacheEvict(cacheNames = "wallet-service::payout", key = "#id"),
          @CacheEvict(cacheNames = "wallet-service::S5-F1", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F3", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F6", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F8", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F9", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F10", allEntries = true),
          @CacheEvict(cacheNames = "wallet-service::S5-F11", allEntries = true)
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

    String reversalScope = request == null || request.getReversalScope() == null
            ? null
            : request.getReversalScope().toString();

    String refundReason = request == null ? null : request.getReason();

    if (!result.isApproved()) {
      payoutAuditService.recordRefundResult(
              payout,
              false,
              result.getAmount(),
              result.getStrategyName(),
              result.getReasonCode(),
              reversalScope,
              refundReason
      );

      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, result.getReasonCode());
    }

    payout.setStatus(PayoutStatus.REFUNDED);

    Map<String, Object> details = payout.getTransactionDetails();
    if (details == null) {
      details = new HashMap<>();
    }

    details.put("refundAmount", result.getAmount());
    details.put("reversalScope", reversalScope);
    details.put("refundReason", refundReason);
    details.put("refundedAt", LocalDateTime.now().toString());

    payout.setTransactionDetails(details);

    Payout saved = payoutRepository.save(payout);

    payoutAuditService.recordRefundResult(
            saved,
            true,
            result.getAmount(),
            result.getStrategyName(),
            result.getReasonCode(),
            reversalScope,
            refundReason
    );

    return new PayoutReversalResultDTO(saved, result);
  }
  @Cacheable(cacheNames = "wallet-service::S5-F9", key = "#limit")
  public List<PromoCodeUsageDTO> getTopUsedPromoCodes(int limit) {
    if (limit <= 0) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
              "Limit must be positive");
    }

    List<Object[]> rows = promoCodeRepository.findTopUsedPromoCodes(limit);
    List<PromoCodeUsageDTO> result = new ArrayList<>();

    for (Object[] row : rows) {
      result.add(promoCodeUsageObjectArrayAdapter.adapt(row));
    }

    return result;
  }

  private void recordLifecycleStatusChange(Payout payout,
                                           PayoutStatus previousStatus,
                                           PayoutStatus newStatus,
                                           String reason) {
    if (previousStatus == newStatus || newStatus == null) {
      return;
    }

    String action = lifecycleActionForStatus(newStatus);
    if (action == null) {
      return;
    }

    Map<String, Object> details = new LinkedHashMap<>();
    details.put("previousStatus", previousStatus == null ? null : previousStatus.name());
    details.put("newStatus", newStatus.name());
    details.put("reason", reason);
    payoutAuditService.recordPayoutEvent(payout, action, details);
  }

  private String lifecycleActionForStatus(PayoutStatus status) {
    return switch (status) {
      case COMPLETED -> PayoutAuditService.COMPLETED;
      case FAILED -> PayoutAuditService.FAILED;
      case REFUNDED -> PayoutAuditService.REFUNDED;
      default -> null;
    };
  }
}
