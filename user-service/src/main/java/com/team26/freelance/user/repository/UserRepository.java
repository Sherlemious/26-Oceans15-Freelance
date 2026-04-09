package com.team26.freelance.user.repository;

import com.team26.freelance.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.time.LocalDateTime;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    @Query(value = "SELECT COUNT(*) FROM contracts WHERE freelancer_id = :userId AND status = 'ACTIVE'",
            nativeQuery = true)
    long countActiveContracts(@Param("userId") Long userId);

    @Modifying
    @Query(value = "UPDATE proposals SET status = 'WITHDRAWN' WHERE freelancer_id = :userId AND status = 'SUBMITTED'",
            nativeQuery = true)
    void withdrawSubmittedProposals(@Param("userId") Long userId);

    @Query(value = "SELECT * FROM users WHERE preferences @> CAST(:pref AS jsonb)",
            nativeQuery = true)
    List<User> findByPreference(@Param("pref") String prefJson);
    @Query(value = """
            SELECT u.id, u.name,
                   COALESCE(SUM(c.agreed_amount), 0) AS total_earnings,
                   COUNT(c.id) AS contract_count
            FROM users u
            JOIN contracts c ON c.freelancer_id = u.id
            WHERE c.status = 'COMPLETED'
              AND c.created_at BETWEEN :startDate AND :endDate
            GROUP BY u.id, u.name
            ORDER BY total_earnings DESC
            LIMIT :limitVal
            """, nativeQuery = true)
    List<Object[]> findTopFreelancersByEarnings(
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            @Param("limitVal") int limitVal);
}