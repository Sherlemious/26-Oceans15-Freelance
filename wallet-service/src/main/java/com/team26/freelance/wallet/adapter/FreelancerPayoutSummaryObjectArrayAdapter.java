package com.team26.freelance.wallet.adapter;

import com.team26.freelance.wallet.dto.FreelancerPayoutSummaryDTO;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class FreelancerPayoutSummaryObjectArrayAdapter {

    public FreelancerPayoutSummaryDTO adapt(Object[] row) {
        String method = (String) row[0];
        long totalPayouts = ((Number) row[1]).longValue();
        double totalAmount = ((Number) row[2]).doubleValue();

        Map<String, Double> methodBreakdown = new LinkedHashMap<>();
        methodBreakdown.put(method, totalAmount);

        return FreelancerPayoutSummaryDTO.builder()
                .freelancerId(null)
                .totalPayouts(totalPayouts)
                .totalAmount(totalAmount)
                .methodBreakdown(methodBreakdown)
                .build();
    }
}