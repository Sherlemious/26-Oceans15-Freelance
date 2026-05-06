package com.team26.freelance.proposal.observer;

import com.team26.freelance.common.event.EventFactory;
import com.team26.freelance.common.event.EventType;
import com.team26.freelance.common.event.MongoEvent;
import com.team26.freelance.common.event.ProposalEvent;
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

            Map<String, Object> details = new HashMap<>();

            if (payload instanceof Proposal) {
                Proposal p = (Proposal) payload;
                params.put("proposalId", p.getId());
                if (p.getJobId() != null) details.put("jobId", p.getJobId());
                if (p.getFreelancerId() != null) details.put("freelancerId", p.getFreelancerId());
                if (p.getStatus() != null) details.put("status", p.getStatus().name());
            }

            if (!details.isEmpty()) {
                params.put("details", details);
            }

            // Create common event via Factory
            MongoEvent event = EventFactory.createEvent(EventType.PROPOSAL, params);

            if (event instanceof ProposalEvent proposalEvent) {
                repository.save(proposalEvent);
            } else {
                System.err.println("WARN: Expected ProposalEvent but got " + event.getClass().getSimpleName());
            }

        } catch (Exception e) {
            System.err.println("WARN: Mongo Event Logging Failed: " + e.getMessage());
        }
    }
}