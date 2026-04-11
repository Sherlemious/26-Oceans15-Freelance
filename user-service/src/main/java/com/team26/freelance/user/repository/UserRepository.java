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

    @Query(value = """
            SELECT u.*
            FROM users u
            LEFT JOIN (
                SELECT c.freelancer_id, COUNT(*) AS completed_count
                FROM contracts c
                WHERE c.status = 'COMPLETED'
                GROUP BY c.freelancer_id
            ) cc ON cc.freelancer_id = u.id
            WHERE u.preferences ->> 'language' = :lang
              AND COALESCE(cc.completed_count, 0) >= :minContracts
            """, nativeQuery = true)
    List<User> findByLanguageWithMinCompletedContracts(
            @Param("lang") String lang,
            @Param("minContracts") int minContracts);

    @Query(value = """
            SELECT u.id,
                   u.name,
                   COUNT(c.id) AS total_contracts,
                   COALESCE(SUM(CASE WHEN c.status = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_contracts,
                   COALESCE(SUM(CASE WHEN c.status = 'TERMINATED' THEN 1 ELSE 0 END), 0) AS terminated_contracts,
                   COALESCE(SUM(CASE WHEN c.status = 'COMPLETED' THEN c.agreed_amount ELSE 0 END), 0) AS total_earnings,
                   COALESCE(AVG(CASE WHEN c.status = 'COMPLETED' THEN c.agreed_amount ELSE NULL END), 0) AS average_contract_value
            FROM users u
            LEFT JOIN contracts c
                   ON c.freelancer_id = u.id OR c.client_id = u.id
            WHERE u.id = :userId
            GROUP BY u.id, u.name
            """, nativeQuery = true)
    List<Object[]> findUserContractSummaryById(@Param("userId") Long userId);
}
