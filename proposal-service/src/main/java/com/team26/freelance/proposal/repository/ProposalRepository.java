package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {          
    @Query(value = """
        SELECT * FROM proposals
        WHERE (:status IS NULL OR status = :status)
          AND submitted_at BETWEEN :startDate AND :endDate
        ORDER BY submitted_at DESC
        """, nativeQuery = true)
    List<Proposal> searchByStatusAndDateRange(
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
           
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
           
    @Query(value = """
        SELECT COUNT(*) FROM proposals
        WHERE status IN ('SUBMITTED', 'SHORTLISTED')
          AND bid_amount BETWEEN :lowerBound AND :upperBound
        """, nativeQuery = true)
    int countActiveSimilarProposals(
            @Param("lowerBound") double lowerBound,
            @Param("upperBound") double upperBound
    );

    @Query(value = "SELECT id FROM contracts WHERE proposal_id = :proposalId AND status = 'ACTIVE' LIMIT 1", nativeQuery = true)
    Long findActiveContractIdByProposalId(@Param("proposalId") Long proposalId);

    @Query(value = "SELECT agreed_amount FROM contracts WHERE id = :contractId", nativeQuery = true)
    Double findContractAgreedAmount(@Param("contractId") Long contractId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE contracts SET status = 'COMPLETED', end_date = NOW() WHERE id = :contractId", nativeQuery = true)
    void markContractAsCompleted(@Param("contractId") Long contractId);

    @Modifying
    @Transactional
    @Query(value = "UPDATE jobs SET status = 'CLOSED' WHERE id = :jobId", nativeQuery = true)
    void updateJobStatusToClosed(@Param("jobId") Long jobId);

    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO payouts (contract_id, freelancer_id, amount, status, created_at) 
        VALUES (:contractId, :freelancerId, :amount, 'PENDING', NOW())
        """, nativeQuery = true)
    void insertPendingPayout(
            @Param("contractId") Long contractId,
            @Param("freelancerId") Long freelancerId,
            @Param("amount") Double amount
    );

}
