package com.team26.freelance.wallet.repository;

import com.team26.freelance.wallet.model.Payout;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PayoutRepository extends JpaRepository<Payout, Long> {

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

    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :userId", nativeQuery = true)
    int countUsersById(@Param("userId") Long userId);

    @Query(value = """
        SELECT method, COUNT(*), SUM(amount)
        FROM payouts
        WHERE freelancer_id = :freelancerId AND status = 'COMPLETED'
        GROUP BY method
        """, nativeQuery = true)
    List<Object[]> getPayoutSummaryByFreelancer(@Param("freelancerId") Long freelancerId);
}
