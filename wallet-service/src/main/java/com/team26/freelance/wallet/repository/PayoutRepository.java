package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.model.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

    @Query(value = """
            SELECT
                COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN p.amount ELSE 0 END), 0) AS totalRevenue,
                COALESCE(SUM(CASE WHEN p.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS totalTransactions,
                COALESCE(SUM(CASE WHEN p.status = 'REFUNDED' THEN p.amount ELSE 0 END), 0) AS refundedAmount,
                COALESCE(SUM(CASE WHEN p.status = 'REFUNDED' THEN 1 ELSE 0 END), 0) AS refundCount
            FROM payouts p
            WHERE p.created_at BETWEEN :startDate AND :endDate
            """, nativeQuery = true)
    Object[] getRevenueReport(@Param("startDate") LocalDateTime startDate,
                              @Param("endDate") LocalDateTime endDate);
}
