package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.model.DiscountType;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutMethod;
import com.team26.freelance.wallet.model.PayoutPromo;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.model.PromoCode;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class PromoCodeRepositoryTest {

    @Autowired
    private PromoCodeRepository promoCodeRepository;

    @Autowired
    private PayoutRepository payoutRepository;

    @Autowired
    private PayoutPromoRepository payoutPromoRepository;

    @Test
    void findTopUsedPromoCodes_aggregatesTotalDiscountGivenCorrectly() {
        PromoCode promoCode = new PromoCode();
        promoCode.setCode("PROMO100");
        promoCode.setDiscountType(DiscountType.FIXED);
        promoCode.setDiscountValue(100.0);
        promoCode.setMaxUses(10);
        promoCode.setCurrentUses(2);
        promoCode.setActive(true);
        promoCode.setExpiryDate(LocalDateTime.now().plusDays(3));
        promoCode = promoCodeRepository.save(promoCode);

        Payout firstPayout = createCompletedPayout();
        Payout secondPayout = createCompletedPayout();

        payoutPromoRepository.save(createPayoutPromo(firstPayout, promoCode, 100.0));
        payoutPromoRepository.save(createPayoutPromo(secondPayout, promoCode, 100.0));

        List<Object[]> rows = promoCodeRepository.findTopUsedPromoCodes(10);
        long promoCodeId = promoCode.getId();

        Object[] promoRow = rows.stream()
                .filter(row -> ((Number) row[0]).longValue() == promoCodeId)
                .findFirst()
                .orElseThrow();

        assertEquals(200.0, ((Number) promoRow[5]).doubleValue());
    }

    private Payout createCompletedPayout() {
        Payout payout = new Payout();
        payout.setContractId(1L);
        payout.setFreelancerId(1L);
        payout.setAmount(1000.0);
        payout.setMethod(PayoutMethod.BANK_TRANSFER);
        payout.setStatus(PayoutStatus.COMPLETED);
        return payoutRepository.save(payout);
    }

    private PayoutPromo createPayoutPromo(Payout payout, PromoCode promoCode, double discountApplied) {
        PayoutPromo payoutPromo = new PayoutPromo();
        payoutPromo.setPayout(payout);
        payoutPromo.setPromoCode(promoCode);
        payoutPromo.setDiscountApplied(discountApplied);
        return payoutPromo;
    }
}
