package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.jpa.repository.Modifying;

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

        List<Proposal> findByStatusAndSubmittedAtBetweenOrderBySubmittedAtDesc(
                        com.team26.freelance.proposal.model.ProposalStatus status,
                        LocalDateTime startDate,
                        LocalDateTime endDate);

        List<Proposal> findBySubmittedAtBetweenOrderBySubmittedAtDesc(
                        LocalDateTime startDate,
                        LocalDateTime endDate);

        List<Proposal> findByStatusOrderBySubmittedAtDesc(
                        com.team26.freelance.proposal.model.ProposalStatus status);

        // User and Job enrichment are performed via Feign clients (no direct cross-service SQL queries)

        // ── Authorization helpers (local data only) ───────────────────────

        @Query(value = "SELECT COUNT(*) > 0 FROM proposals WHERE id = :proposalId AND freelancer_id = :freelancerId", nativeQuery = true)
        boolean isProposalOwnedByFreelancer(@Param("proposalId") Long proposalId,
                        @Param("freelancerId") Long freelancerId);

        @Query(value = """
                        SELECT COUNT(*) FROM proposals
                        WHERE status IN ('SUBMITTED', 'SHORTLISTED')
                          AND bid_amount BETWEEN :lowerBound AND :upperBound
                        """, nativeQuery = true)
        int countActiveSimilarProposals(
                        @Param("lowerBound") double lowerBound,
                        @Param("upperBound") double upperBound);

        List<Proposal> findByStatusAndPaymentPendingAtBefore(
                        com.team26.freelance.proposal.model.ProposalStatus status,
                        LocalDateTime paymentPendingAt);

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
        @Query(value = "UPDATE proposals SET status = 'REJECTED' WHERE job_id = :jobId AND status IN ('SUBMITTED', 'SHORTLISTED')", nativeQuery = true)
        void rejectActiveProposalsForJob(@Param("jobId") Long jobId);

        @Modifying
        @Transactional
        @Query(value = "UPDATE jobs SET status = 'OPEN' WHERE id = :jobId AND status = 'IN_PROGRESS'", nativeQuery = true)
        void reopenJob(@Param("jobId") Long jobId);

        @Query(value = """
                        SELECT *
                        FROM proposals
                        WHERE metadata IS NOT NULL
                          AND metadata @> CAST(:jsonFilter AS jsonb)
                        """, nativeQuery = true)
        List<Proposal> findByMetadataContains(@Param("jsonFilter") String jsonFilter);

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
                        @Param("endDate") LocalDateTime endDate);

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
                        @Param("endDate") LocalDateTime endDate);

        // Enrichment and existence checks must go through Feign clients to respect service boundaries.

        @Query(value = """
                        SELECT COUNT(id),
                               SUM(CASE WHEN status = 'ACCEPTED' THEN 1 ELSE 0 END),
                               COALESCE(AVG(bid_amount), 0.0),
                               COALESCE(MIN(bid_amount), 0.0),
                               COALESCE(MAX(bid_amount), 0.0)
                        FROM proposals
                        WHERE job_id = :jobId
                          AND (:start IS NULL OR submitted_at >= :start)
                          AND (:end IS NULL OR submitted_at <= :end)
                        """, nativeQuery = true)
        List<Object[]> getJobProposalSummaryAggregations(
                @Param("jobId") Long jobId,
                @Param("start") LocalDateTime start,
                @Param("end") LocalDateTime end);

}
