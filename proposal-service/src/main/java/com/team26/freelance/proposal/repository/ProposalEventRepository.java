package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.ProposalEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalEventRepository extends MongoRepository<ProposalEvent, String> {
}