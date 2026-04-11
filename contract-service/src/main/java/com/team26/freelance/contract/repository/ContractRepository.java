package com.team26.freelance.contract.repository;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    @Query("SELECT COUNT(c) FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN ('COMPLETED', 'TERMINATED')")
    long countPurgeable(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Transactional
    @Query("DELETE FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN ('COMPLETED', 'TERMINATED')")
    int deleteOldContracts(@Param("cutoff") LocalDateTime cutoff);
}
	List<Contract> findByCreatedAtBetweenOrderByCreatedAtAsc(LocalDateTime startDateTime, LocalDateTime endDateTime);

	List<Contract> findByCreatedAtBetweenAndStatusOrderByCreatedAtAsc(
			LocalDateTime startDateTime,
			LocalDateTime endDateTime,
			ContractStatus status
	);

	@Query(value = "SELECT * FROM contracts WHERE metadata->>:key = :value", nativeQuery = true)
	List<Contract> findByMetadataEquals(@Param("key") String key, @Param("value") String value);

	@Query(value = "SELECT * FROM contracts WHERE CAST(metadata->>:key AS numeric) > CAST(:value AS numeric)", nativeQuery = true)
	List<Contract> findByMetadataGreaterThan(@Param("key") String key, @Param("value") String value);

	@Query(value = "SELECT * FROM contracts WHERE CAST(metadata->>:key AS numeric) < CAST(:value AS numeric)", nativeQuery = true)
	List<Contract> findByMetadataLessThan(@Param("key") String key, @Param("value") String value);
  
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
  FreelancerPerformanceProjection getFreelancerPerformance(@Param("freelancerId") Long freelancerId,
                                      @Param("startDate") java.time.LocalDateTime startDate,
                                      @Param("endDate") java.time.LocalDateTime endDate);

    @Query(value = """
        SELECT 
            c.id as contractId,
            u.name as freelancerName,
            j.title as jobTitle,
            c.agreed_amount as agreedAmount,
            COALESCE(CAST(c.metadata->>'progressPercentage' AS numeric), 0) as progressPercentage,
            EXTRACT(EPOCH FROM (NOW() - COALESCE(CAST(c.metadata->>'lastActivityDate' AS timestamp), c.created_at)))/86400 as daysSinceLastActivity
        FROM contracts c
        JOIN users u ON c.freelancer_id = u.id
        JOIN jobs j ON c.job_id = j.id
        WHERE c.status = 'ACTIVE'
          AND COALESCE(CAST(c.metadata->>'progressPercentage' AS numeric), 0) <= :maxProgress
          AND (EXTRACT(EPOCH FROM (NOW() - COALESCE(CAST(c.metadata->>'lastActivityDate' AS timestamp), c.created_at)))/86400) > :stalledDays
    """, nativeQuery = true)
    java.util.List<Object[]> findStalledContracts(@Param("maxProgress") double maxProgress,
                                                  @Param("stalledDays") double stalledDays);
}
