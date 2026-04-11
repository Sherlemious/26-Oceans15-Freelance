package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.ProposalMilestone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalMilestoneRepository extends JpaRepository<ProposalMilestone, Long> {
    @Query(value = "SELECT COUNT(*) FROM proposal_milestones WHERE proposal_id = :proposalId", nativeQuery = true)
    int countByProposalId(@Param("proposalId") Long proposalId);

    @Query("SELECT COALESCE(MAX(pm.milestoneOrder), 0) FROM ProposalMilestone pm WHERE pm.proposal.id = :proposalId")
    int findMaxMilestoneOrderByProposalId(@Param("proposalId") Long proposalId);

    @Query("SELECT COALESCE(SUM(pm.amount), 0) FROM ProposalMilestone pm WHERE pm.proposal.id = :proposalId")
    double sumAmountsByProposalId(@Param("proposalId") Long proposalId);
}
