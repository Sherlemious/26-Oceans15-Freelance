package com.team26.freelance.proposal.observer;

import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;
import com.team26.freelance.proposal.model.Proposal;
import com.team26.freelance.proposal.repository.ProposalEventRepository;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class MongoEventLogger implements EntityObserver {

    private final ProposalEventRepository repository;

    // Removed the EventFactory from the constructor!
    public MongoEventLogger(ProposalEventRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onEvent(String eventType, Object payload) {
        try {
            Map<String, Object> params = new HashMap<>();
            params.put("action", eventType);

            if (payload instanceof Proposal) {
                Proposal p = (Proposal) payload;
                params.put("proposalId", p.getId());
                if (p.getJobId() != null) params.put("jobId", p.getJobId());
                if (p.getFreelancerId() != null) params.put("freelancerId", p.getFreelancerId());
                if (p.getStatus() != null) params.put("status", p.getStatus().name());
            }

            // Create common event via Factory
            MongoEvent event = EventFactory.createEvent(EventType.PROPOSAL, params);

            // Map it to the local Database Entity to avoid ClassCastException
            com.team26.freelance.proposal.model.ProposalEvent dbEntity =
                    new com.team26.freelance.proposal.model.ProposalEvent(
                            null,
                            event.getAction(),
                            event.getTimestamp(),
                            event.getDetails()
                    );

            repository.save(dbEntity);

        } catch (Exception e) {
            System.err.println("WARN: Mongo Event Logging Failed: " + e.getMessage());
        }
    }
}