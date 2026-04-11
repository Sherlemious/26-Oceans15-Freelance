package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.dto.RevenueReportProjection;
import com.team26.freelance.wallet.model.Payout;
import com.team26.freelance.wallet.model.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
            WHERE (:status IS NULL OR status = :status)
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

    Optional<Payout> findFirstByContractIdAndStatus(Long contractId, PayoutStatus status);
}
