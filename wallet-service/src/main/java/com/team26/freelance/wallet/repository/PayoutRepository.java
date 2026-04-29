package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.dto.RevenueReportProjection;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import com.team26.freelance.wallet.dto.CategoryRevenueProjection;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
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
            WHERE (:status IS NULL OR status = :status::payout_status)
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

    @Query(value = "SELECT status::text FROM contracts WHERE id = :contractId", nativeQuery = true)
    Optional<String> findContractStatusById(@Param("contractId") Long contractId);

    boolean existsByContractIdAndStatus(Long contractId, PayoutStatus status);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Payout> findFirstByContractIdAndStatusOrderByCreatedAtAsc(Long contractId, PayoutStatus status);

    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :userId", nativeQuery = true)
    int countUsersById(@Param("userId") Long userId);

    @Query(value = """
            SELECT method, COUNT(*), SUM(amount)
            FROM payouts
            WHERE freelancer_id = :freelancerId AND status = 'COMPLETED'
            GROUP BY method
            """, nativeQuery = true)
    List<Object[]> getPayoutSummaryByFreelancer(@Param("freelancerId") Long freelancerId);

    @Query(value = """
        SELECT
            j.category::text AS "jobCategory",
            COALESCE(SUM(COALESCE((p.transaction_details ->> 'platformFee')::numeric, p.amount * 0.10)), 0) AS "totalFees",
            COALESCE(AVG(COALESCE((p.transaction_details ->> 'platformFee')::numeric, p.amount * 0.10)), 0) AS "averageFee",
            COUNT(*) AS "payoutCount"
        FROM payouts p
        JOIN contracts c ON p.contract_id = c.id
        JOIN jobs j ON c.job_id = j.id
        WHERE p.status = 'COMPLETED'
          AND p.created_at >= :startDate
          AND p.created_at < :endExclusive
        GROUP BY j.category
        ORDER BY "totalFees" DESC
        """, nativeQuery = true)
    List<CategoryRevenueProjection> getPlatformFeeAnalyticsByCategory(
            @Param("startDate") LocalDateTime startDate,
            @Param("endExclusive") LocalDateTime endExclusive
    );


    @Query(value = """
        SELECT
            j.category::text AS "jobCategory",
            COALESCE(SUM(COALESCE((p.transaction_details ->> 'platformFee')::numeric, p.amount * 0.10)), 0) AS "totalFees",
            COALESCE(AVG(COALESCE((p.transaction_details ->> 'platformFee')::numeric, p.amount * 0.10)), 0) AS "averageFee",
            COUNT(*) AS "payoutCount"
        FROM payouts p
        JOIN contracts c ON p.contract_id = c.id
        JOIN jobs j ON c.job_id = j.id
        WHERE p.status = 'COMPLETED'
        GROUP BY j.category
        ORDER BY "totalFees" DESC
        """, nativeQuery = true)
    List<CategoryRevenueProjection> getPlatformFeeAnalyticsByCategoryAllTime();
}
