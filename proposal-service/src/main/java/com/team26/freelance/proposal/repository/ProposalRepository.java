package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    @Query(value = "SELECT role FROM users WHERE id = :freelancerId", nativeQuery = true)
    String findFreelancerRole(@Param("freelancerId") Long freelancerId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE jobs SET status = 'IN_PROGRESS' WHERE id = :jobId", nativeQuery = true)
    void updateJobStatusToInProgress(@Param("jobId") Long jobId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO contracts (job_id, freelancer_id, client_id, proposal_id,
                               agreed_amount, status, start_date, created_at)
        SELECT p.job_id,
               p.freelancer_id,
               j.client_id,
               p.id,
               p.bid_amount,
               'ACTIVE',
               NOW(),
               NOW()
        FROM proposals p
        JOIN jobs j ON j.id = p.job_id
        WHERE p.id = :proposalId
        """, nativeQuery = true)
    void insertContractFromProposal(@Param("proposalId") Long proposalId);
}
