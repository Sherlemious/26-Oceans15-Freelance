package com.team26.freelance.wallet.strategy;

import com.team26.freelance.wallet.dto.RefundRequest;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.repository.PayoutRepository;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

@Component
public class RefundStrategySelector {

    private final PayoutRepository payoutRepository;

    public RefundStrategySelector(PayoutRepository payoutRepository) {
        this.payoutRepository = payoutRepository;
    }

    public RefundStrategy select(Payout payout, RefundRequest request) {
        long daysOld = ChronoUnit.DAYS.between(payout.getCreatedAt(), LocalDateTime.now());
        if (daysOld > 30) {
            return new NoReversalStrategy();
        }
        if ("MILESTONE_ONLY".equalsIgnoreCase(request.getReversalScope())) {
            Double milestoneSum = payoutRepository.sumUnresolvedMilestoneAmounts(payout.getContractId());
            double amount = milestoneSum != null ? milestoneSum : 0.0;
            return new MilestoneReversalStrategy(amount);
        }
        return new FullPayoutReversalStrategy();
    }
}
