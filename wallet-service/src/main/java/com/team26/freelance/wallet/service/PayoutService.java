package com.team26.freelance.wallet.service;

import com.team26.freelance.wallet.dto.AppliedPromoCodeDTO;
import com.team26.freelance.wallet.dto.FreelancerPayoutSummaryDTO;
import com.team26.freelance.wallet.dto.PayoutDetailsDTO;
import com.team26.freelance.wallet.dto.PayoutResponseDTO;
import com.team26.freelance.wallet.dto.PayoutReversalResultDTO;
import com.team26.freelance.wallet.dto.ProcessContractPayoutRequest;
import com.team26.freelance.wallet.dto.PromoCodeUsageDTO;
import com.team26.freelance.wallet.model.DiscountType;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutPromo;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.model.PromoCode;
import com.team26.freelance.wallet.repository.PayoutPromoRepository;
import com.team26.freelance.wallet.repository.PayoutRepository;
import com.team26.freelance.wallet.repository.PromoCodeRepository;
import com.team26.freelance.wallet.strategy.PayoutReversalContext;
import com.team26.freelance.wallet.strategy.PayoutReversalResult;
import com.team26.freelance.wallet.dto.CategoryRevenueDTO;
import com.team26.freelance.wallet.model.PayoutAuditEvent;
import com.team26.freelance.wallet.repository.PayoutAuditEventRepository;
import org.springframework.context.ApplicationEventPublisher;
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
  private final PayoutReversalContext payoutReversalContext;
  private final ApplicationEventPublisher eventPublisher;
  private final PlatformFeeAnalyticsService platformFeeAnalyticsService;
  private final PayoutAuditEventRepository payoutAuditEventRepository;

  public PayoutService(PayoutRepository payoutRepository,
                       PromoCodeRepository promoCodeRepository,
                       PayoutPromoRepository payoutPromoRepository,
                       PayoutReversalContext payoutReversalContext,
                       ApplicationEventPublisher eventPublisher,
                       PlatformFeeAnalyticsService platformFeeAnalyticsService,
                       PayoutAuditEventRepository payoutAuditEventRepository) {
    this.payoutRepository = payoutRepository;
    this.promoCodeRepository = promoCodeRepository;
    this.payoutPromoRepository = payoutPromoRepository;
    this.payoutReversalContext = payoutReversalContext;
    this.eventPublisher = eventPublisher;
    this.platformFeeAnalyticsService = platformFeeAnalyticsService;
    this.payoutAuditEventRepository = payoutAuditEventRepository;
  }

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

  public Payout getPayoutById(Long id) {
    return payoutRepository.findById(id).orElseThrow(
        ()
            -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                                           "Payout not found"));
  }

  public Payout createPayout(Payout payout) {
    return payoutRepository.save(payout);
  }

  @Transactional
  public Payout processContractPayout(Long contractId,
                                      ProcessContractPayoutRequest request) {
    String contractStatus =
        payoutRepository.findContractStatusById(contractId)
            .orElseThrow(()
                             -> new ResponseStatusException(
                                 HttpStatus.NOT_FOUND, "Contract not found"));

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
            .orElseThrow(()
                             -> new ResponseStatusException(
                                 HttpStatus.BAD_REQUEST,
                                 "Pending payout not found for this contract"));

    if (request.getMethod() == null) {
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                                        "Payout method is required");
    }

    String accountLastFour = request.getAccountLastFour();
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
    transactionDetails.put("method", request.getMethod().name());
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
    pendingPayout.setMethod(request.getMethod());
    pendingPayout.setTransactionDetails(transactionDetails);

    return payoutRepository.save(pendingPayout);
  }

  public Payout updatePayout(Long id, Payout updated) {
    Payout existing = getPayoutById(id);
    existing.setContractId(updated.getContractId());
    existing.setFreelancerId(updated.getFreelancerId());
    existing.setAmount(updated.getAmount());
    existing.setMethod(updated.getMethod());
    existing.setStatus(updated.getStatus());
    existing.setTransactionDetails(updated.getTransactionDetails());
    return payoutRepository.save(existing);
  }

  public void deletePayout(Long id) {
    getPayoutById(id);
    payoutRepository.deleteById(id);
  }

  public List<Payout> searchByStatusAndDateRange(String status,
                                                 LocalDate startDate,
                                                 LocalDate endDate) {
    if (startDate == null || endDate == null) {
      return payoutRepository.findAll();
    }
    LocalDateTime start = startDate.atStartOfDay();
    LocalDateTime end = endDate.atTime(23, 59, 59);
    return payoutRepository.searchByStatusAndDateRange(status, start, end);
  }

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
    payout.getTransactionDetails().put("refundReason", reason);
    payout.getTransactionDetails().put("refundedAt",
                                       LocalDateTime.now().toString());
    return payoutRepository.save(payout);
  }

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

    return payoutRepository.save(payout);
  }
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

  public FreelancerPayoutSummaryDTO getFreelancerPayoutSummary(Long freelancerId) {
    if (payoutRepository.countUsersById(freelancerId) == 0) {
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
    }
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

  @Transactional
  public PayoutReversalResultDTO reversePayout(Long id) {
    Payout payout = payoutRepository.findByIdWithPromos(id).orElseThrow(
        () -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payout not found"));

    PayoutReversalResult result = payoutReversalContext.executeStrategy(payout);

    if (result.isApproved()) {
      payout.setStatus(PayoutStatus.REFUNDED);
      Map<String, Object> details = payout.getTransactionDetails();
      if (details == null) {
        details = new HashMap<>();
      }
      details.put("refundedAt", LocalDateTime.now().toString());
      details.put("strategyApplied", result.getStrategyApplied());
      details.put("amountReturned", result.getAmountReturned());
      payout.setTransactionDetails(details);
      payoutRepository.save(payout);
    }

    eventPublisher.publishEvent(new PayoutAuditPendingEvent(
        payout.getId(),
        result.isApproved() ? "REFUNDED" : "REFUND_DENIED",
        result.getAmountReturned(),
        result.getStrategyApplied(),
        result.getReason(),
        LocalDateTime.now()
    ));

    return new PayoutReversalResultDTO(payout, result);
  }

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

  public List<CategoryRevenueDTO> getPlatformFeeAnalyticsByCategory() {
    PayoutAuditEvent auditEvent = new PayoutAuditEvent();
    auditEvent.setPayoutId(null);
    auditEvent.setEventType("ANALYTICS_VIEWED");
    auditEvent.setAmountReturned(null);
    auditEvent.setStrategyApplied(null);
    auditEvent.setReason("Platform fee analytics viewed");
    auditEvent.setTimestamp(LocalDateTime.now());
    payoutAuditEventRepository.save(auditEvent);

    return platformFeeAnalyticsService.getPlatformFeeAnalyticsAllTime();
  }
}
