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
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ContractRepository extends JpaRepository<Contract, Long> {

    long countByFreelancerId(Long freelancerId);

    long countByFreelancerIdAndStatus(Long freelancerId, ContractStatus status);

    long countByJobIdAndStatus(Long jobId, ContractStatus status);

    Optional<Contract> findFirstByFreelancerIdAndStatusOrClientIdAndStatusOrderByCreatedAtDesc(Long freelancerId, ContractStatus status1, Long clientId, ContractStatus status2);

    @Query(value = "SELECT * FROM contracts WHERE (freelancer_id=:userId OR client_id=:userId) AND CAST(status AS VARCHAR) = 'ACTIVE' ORDER BY created_at DESC LIMIT 1", nativeQuery = true)
    Optional<Contract> findMostRecentActiveContractByUserIdNative(@Param("userId") Long userId);

    Optional<Contract> findFirstByProposalIdAndStatusOrderByCreatedAtDesc(Long proposalId, ContractStatus status);

    List<Contract> findByAgreedAmountBetweenAndStatusOrderByAgreedAmountDesc(Double minAmount, Double maxAmount, ContractStatus status);

    List<Contract> findByAgreedAmountBetweenOrderByAgreedAmountDesc(Double minAmount, Double maxAmount);
    
    @Query("SELECT COUNT(c) FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN ('COMPLETED', 'TERMINATED')")
    long countPurgeable(@Param("cutoff") LocalDateTime cutoff);

    @Query("SELECT c.id FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN :statuses")
    List<Long> findPurgeableIds(@Param("cutoff") LocalDateTime cutoff,
                                @Param("statuses") Collection<ContractStatus> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Transactional
    @Query("DELETE FROM Contract c WHERE c.createdAt < :cutoff AND c.status IN ('COMPLETED', 'TERMINATED')")
    int deleteOldContracts(@Param("cutoff") LocalDateTime cutoff);
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
  
  @Query(value = """
      SELECT 
          COUNT(*) as totalContracts,
          COALESCE(AVG(agreed_amount), 0) as averageContractValue,
          COALESCE(SUM(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN agreed_amount ELSE 0 END), 0) as totalEarnings,
          CASE WHEN COUNT(*) = 0 THEN 0 ELSE (COUNT(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN 1 END) * 100.0 / COUNT(*)) END as completionRate,
          COALESCE(AVG(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN EXTRACT(EPOCH FROM (end_date - start_date))/86400 ELSE NULL END), 0) as averageDurationDays
      FROM contracts
      WHERE freelancer_id = :freelancerId
        AND start_date >= :startDate AND start_date <= :endDate
  """, nativeQuery = true)
  FreelancerPerformanceProjection getFreelancerPerformance(@Param("freelancerId") Long freelancerId,
                                      @Param("startDate") java.time.LocalDateTime startDate,
                                      @Param("endDate") java.time.LocalDateTime endDate);

    @Query(value = """
        SELECT
            COUNT(*) AS total_contracts,
            COALESCE(SUM(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN 1 ELSE 0 END), 0) AS completed_contracts,
            COALESCE(SUM(CASE WHEN CAST(status AS VARCHAR) = 'TERMINATED' THEN 1 ELSE 0 END), 0) AS terminated_contracts,
            COALESCE(SUM(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN agreed_amount ELSE 0 END), 0) AS total_earnings,
            COALESCE(AVG(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN agreed_amount ELSE NULL END), 0) AS average_contract_value
        FROM contracts
        WHERE freelancer_id = :userId
    """, nativeQuery = true)
    Object[] getUserContractSummary(@Param("userId") Long userId);

    @Query(value = """
        SELECT
            COUNT(*) as totalContracts,
            COALESCE(AVG(agreed_amount), 0) as averageContractValue,
            CASE
                WHEN COUNT(*) = 0 THEN 0
                ELSE COUNT(CASE WHEN CAST(status AS VARCHAR) = 'COMPLETED' THEN 1 END) * 1.0 / COUNT(*)
            END as completionRate,
            COALESCE(AVG(CASE
                WHEN CAST(status AS VARCHAR) = 'COMPLETED' AND end_date IS NOT NULL
                    THEN EXTRACT(EPOCH FROM (end_date - start_date)) / 86400.0
                ELSE NULL
            END), 0) as averageContractDurationDays
        FROM contracts
        WHERE start_date >= :startDate
          AND start_date <= :endDate
    """, nativeQuery = true)
    ContractAnalyticsProjection getContractAnalytics(@Param("startDate") LocalDateTime startDate,
                                                     @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT CAST(status AS VARCHAR) as status, COUNT(*) as count
        FROM contracts
        WHERE start_date >= :startDate
          AND start_date <= :endDate
        GROUP BY status
    """, nativeQuery = true)
    List<Object[]> countContractsByStatus(@Param("startDate") LocalDateTime startDate,
                                          @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT id, CAST(status AS VARCHAR), agreed_amount, start_date, end_date
        FROM contracts
        WHERE start_date >= :startDate
          AND start_date <= :endDate
        ORDER BY id
    """, nativeQuery = true)
    List<Object[]> findContractAnalyticsSourceSignatures(@Param("startDate") LocalDateTime startDate,
                                                         @Param("endDate") LocalDateTime endDate);

    @Query(value = """
        SELECT *
        FROM contracts c
        WHERE CAST(c.status AS VARCHAR) = 'ACTIVE'
          AND COALESCE(CAST(c.metadata->>'progressPercentage' AS numeric), 0) <= :maxProgress
          AND (EXTRACT(EPOCH FROM (NOW() - COALESCE(CAST(c.metadata->>'lastActivityDate' AS timestamp), c.created_at)))/86400) > :stalledDays
    """, nativeQuery = true)
    java.util.List<Contract> findStalledContractCandidates(@Param("maxProgress") double maxProgress,
                                                           @Param("stalledDays") double stalledDays);
}
