package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.dto.RevenueReportProjection;
import com.team26.freelance.wallet.dto.ContractDataProjection;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0 END), 0) AS "totalRevenue",
                COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS "totalTransactions",
                COALESCE(SUM(CASE WHEN p.status = 'REFUNDED' THEN p.amount ELSE 0 END), 0) AS "refundedAmount",
                COALESCE(SUM(CASE WHEN p.status = 'REFUNDED' THEN 1 ELSE 0 END), 0) AS "refundCount"
            FROM payouts p
            WHERE p.created_at >= :startDate
              AND p.created_at < :endExclusive
            """, nativeQuery = true)
    RevenueReportProjection getRevenueReport(@Param("startDate") LocalDateTime startDate,
                                             @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
            SELECT * FROM payouts
            WHERE (:status IS NULL OR status::text = :status)
              AND created_at BETWEEN :startDate AND :endDate
            ORDER BY created_at DESC
            """, nativeQuery = true)
    List<Payout> searchByStatusAndDateRange(
            @Param("status") String status,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );

    @Query("""
            SELECT DISTINCT p
            FROM Payout p
            LEFT JOIN FETCH p.payoutPromos pp
            LEFT JOIN FETCH pp.promoCode
            WHERE p.id = :id
            """)
    Optional<Payout> findByIdWithPromos(@Param("id") Long id);

    @Query(value = "SELECT status::text AS contractStatus, agreed_amount AS agreedAmount, freelancer_id AS freelancerId FROM contracts WHERE id = :contractId FOR UPDATE", nativeQuery = true)
    List<ContractDataProjection> findContractDataById(@Param("contractId") Long contractId);

    boolean existsByContractIdAndStatus(Long contractId, PayoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payout> findFirstByContractIdAndStatusOrderByCreatedAtAsc(Long contractId, PayoutStatus status);

    Optional<Payout> findFirstByContractIdAndStatusInOrderByCreatedAtAsc(Long contractId,
                                                                          Collection<PayoutStatus> statuses);

    @Query(value = """
            SELECT *
            FROM payouts
            WHERE transaction_details ->> 'proposalId' = CAST(:proposalId AS text)
              AND status IN ('PENDING', 'COMPLETED')
            ORDER BY created_at ASC
            LIMIT 1
            """, nativeQuery = true)
    Optional<Payout> findRefundCandidateByProposalId(@Param("proposalId") Long proposalId);

    @Query(value = """
            SELECT method, COUNT(*), SUM(amount)
            FROM payouts
            WHERE freelancer_id = :freelancerId AND status = 'COMPLETED'
            GROUP BY method
            """, nativeQuery = true)
    List<Object[]> getPayoutSummaryByFreelancer(@Param("freelancerId") Long freelancerId);

    @Query("""
            SELECT COALESCE(SUM(p.amount), 0.0)
            FROM Payout p
            WHERE p.freelancerId = :freelancerId
              AND p.status = :status
              AND p.createdAt >= :startDate
              AND p.createdAt < :endExclusive
            """)
    Double getCompletedPayoutTotalByFreelancer(@Param("freelancerId") Long freelancerId,
                                               @Param("status") PayoutStatus status,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endExclusive") LocalDateTime endExclusive);

    @Query(value = """
            SELECT COALESCE(SUM(pm.amount), 0)
            FROM proposal_milestones pm
            WHERE pm.proposal_id = (
                SELECT proposal_id FROM contracts WHERE id = :contractId
            )
            AND pm.status NOT IN ('COMPLETED', 'APPROVED')
            """, nativeQuery = true)
    Double sumUnresolvedMilestoneAmounts(@Param("contractId") Long contractId);


    @Query("""
            SELECT p
            FROM Payout p
            WHERE p.status = :status
              AND p.createdAt >= :startDate
              AND p.createdAt < :endExclusive
            """)
    List<Payout> findByStatusAndCreatedAtRange(@Param("status") PayoutStatus status,
                                               @Param("startDate") LocalDateTime startDate,
                                               @Param("endExclusive") LocalDateTime endExclusive);
}
