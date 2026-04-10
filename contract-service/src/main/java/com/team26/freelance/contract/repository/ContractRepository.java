package com.team26.freelance.contract.repository;

import com.team26.freelance.contract.model.Contract;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query(value = "SELECT COUNT(*) FROM users WHERE id = :freelancerId", nativeQuery = true)
    long countUserById(@Param("freelancerId") Long freelancerId);

    @Query(value = """
        SELECT 
            COUNT(*) as totalContracts,
            COALESCE(AVG(agreed_amount), 0) as averageContractValue,
            COALESCE(SUM(CASE WHEN status = 'COMPLETED' THEN agreed_amount ELSE 0 END), 0) as totalEarnings,
            CASE WHEN COUNT(*) = 0 THEN 0 ELSE (COUNT(CASE WHEN status = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*)) END as completionRate,
            COALESCE(AVG(CASE WHEN status = 'COMPLETED' THEN EXTRACT(EPOCH FROM (end_date - start_date))/86400 ELSE NULL END), 0) as averageDurationDays
        FROM contracts
        WHERE freelancer_id = :freelancerId
          AND start_date >= :startDate AND start_date <= :endDate
    """, nativeQuery = true)
    Object[][] getFreelancerPerformance(@Param("freelancerId") Long freelancerId,
                                        @Param("startDate") java.time.LocalDateTime startDate,
                                        @Param("endDate") java.time.LocalDateTime endDate);
}
