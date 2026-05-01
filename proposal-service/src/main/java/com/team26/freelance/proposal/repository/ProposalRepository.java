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
import java.util.Optional;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
        @Query("""
                        SELECT DISTINCT p FROM Proposal p
                        LEFT JOIN FETCH p.proposalMilestones m
                        WHERE p.id = :proposalId
                        ORDER BY m.milestoneOrder ASC
                        """)
        Optional<Proposal> findByIdWithMilestones(@Param("proposalId") Long proposalId);

        @Query(value = """
                        SELECT * FROM proposals
                        WHERE status = :status
                          AND submitted_at BETWEEN :startDate AND :endDate
                        ORDER BY submitted_at DESC
                        """, nativeQuery = true)
        List<Proposal> searchByStatusAndDateRange(
                        @Param("status") String status,
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query(value = """
                        SELECT * FROM proposals
                        WHERE submitted_at BETWEEN :startDate AND :endDate
                        ORDER BY submitted_at DESC
                        """, nativeQuery = true)
        List<Proposal> searchByDateRange(
                        @Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);

        @Query(value = """
                        SELECT * FROM proposals
                        WHERE status = :status
                        ORDER BY submitted_at DESC
                        """, nativeQuery = true)
        List<Proposal> searchByStatus(
                        @Param("status") String status);

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
                        @Param("upperBound") double upperBound);

        @Query(value = "SELECT id FROM contracts WHERE proposal_id = :proposalId AND status = 'ACTIVE' LIMIT 1", nativeQuery = true)
        Long findActiveContractIdByProposalId(@Param("proposalId") Long proposalId);

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
                        INSERT INTO payouts (contract_id, freelancer_id, amount, method, status, created_at)
                        VALUES (:contractId, :freelancerId, :amount, 'BANK_TRANSFER', 'PENDING', NOW())
                        """, nativeQuery = true)
        void insertPendingPayout(
                        @Param("contractId") Long contractId,
                        @Param("freelancerId") Long freelancerId,
                        @Param("amount") Double amount);

        @Query(value = "SELECT COUNT(*) FROM proposals WHERE job_id = :jobId AND status IN ('SUBMITTED', 'SHORTLISTED')", nativeQuery = true)
        int countActiveProposals(@Param("jobId") Long jobId);

        @Modifying
        @Transactional
        @Query(value = "UPDATE jobs SET status = 'OPEN' WHERE id = :jobId AND status = 'IN_PROGRESS'", nativeQuery = true)
        void reopenJob(@Param("jobId") Long jobId);

        @Query(value = "SELECT * FROM proposals WHERE metadata @> jsonb_build_object(:jsonKey, :jsonValue)", nativeQuery = true)
        List<Proposal> findByMetadataField(@Param("jsonKey") String key, @Param("jsonValue") String value);

        @Query(value = """
                        SELECT
                            COUNT(id),
                            SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END),
                            SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END),
                            COALESCE(SUM(bid_amount), 0.0)
                        FROM proposals
                        WHERE submitted_at >= :startDate AND submitted_at <= :endDate
                        """, nativeQuery = true)
        List<Object[]> getProposalAnalyticsRawData(@Param("startDate") LocalDateTime startDate,
                        @Param("endDate") LocalDateTime endDate);
        @Query(value = """
                        SELECT status, COUNT(*) as count
                        FROM proposals
                        WHERE submitted_at BETWEEN :startDate AND :endDate
                        GROUP BY status
                        """, nativeQuery = true)
        List<Object[]> countByStatusInRange(
                @Param("startDate") LocalDateTime startDate,
                @Param("endDate") LocalDateTime endDate
        );

        @Query(value = """
                       SELECT 
                        COUNT(*) as total,
                        AVG(bid_amount) as avgBid,
                        AVG(estimated_days) as avgDays,
                        SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END) as accepted
                       FROM proposals
                       WHERE submitted_at BETWEEN :startDate AND :endDate
                       """, nativeQuery = true)
        List<Object[]> getAggregateStats(
                @Param("startDate") LocalDateTime startDate,
                @Param("endDate") LocalDateTime endDate
        );

}
