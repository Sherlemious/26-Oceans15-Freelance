package com.team26.freelance.proposal.adapter;

import com.team26.freelance.proposal.dto.ProposalAnalyticsDashboardDTO;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    public ProposalAnalyticsDashboardDTO adapt(Document document) {
        long totalProposals = document.getLong("totalProposals") != null
                ? document.getLong("totalProposals") : 0L;
        double acceptanceRate = document.getDouble("acceptanceRate") != null
                ? document.getDouble("acceptanceRate") : 0.0;
        double averageBidAmount = document.getDouble("averageBidAmount") != null
                ? document.getDouble("averageBidAmount") : 0.0;
        double averageEstimatedDays = document.getDouble("averageEstimatedDays") != null
                ? document.getDouble("averageEstimatedDays") : 0.0;

        Map<String, Long> proposalsByStatus = new HashMap<>();
        Object statusMap = document.get("proposalsByStatus");
        if (statusMap instanceof Map) {
            ((Map<?, ?>) statusMap).forEach((k, v) ->
                    proposalsByStatus.put(k.toString(), ((Number) v).longValue()));
        }

        return ProposalAnalyticsDashboardDTO.builder()
                .withTotalProposals(totalProposals)
                .withAcceptanceRate(acceptanceRate)
                .withAverageBidAmount(averageBidAmount)
                .withAverageEstimatedDays(averageEstimatedDays)
                .withProposalsByStatus(proposalsByStatus)
                .build();
    }
}