package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.ProposalMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalMilestoneRepository extends JpaRepository<ProposalMilestone, Long> {
}
