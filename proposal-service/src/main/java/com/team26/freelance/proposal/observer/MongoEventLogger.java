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

            // Capital 'E' for the static method call!
            MongoEvent event = EventFactory.createEvent(EventType.PROPOSAL, params);
            repository.save((com.team26.freelance.proposal.model.ProposalEvent) event);

        } catch (Exception e) {
            System.err.println("WARN: Mongo Event Logging Failed: " + e.getMessage());
        }
    }
}