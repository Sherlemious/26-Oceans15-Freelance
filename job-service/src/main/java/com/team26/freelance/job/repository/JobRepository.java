package com.team26.freelance.job.repository;

import com.team26.freelance.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query(value = "SELECT COUNT(*) > 0 FROM contracts WHERE job_id = :jobId AND status = 'ACTIVE'", nativeQuery = true)
    boolean existsActiveContractByJobId(@Param("jobId") Long jobId);

    @Modifying
    @Query(value = "UPDATE proposals SET status = 'REJECTED' WHERE job_id = :jobId AND status = 'SUBMITTED'", nativeQuery = true)
    void rejectSubmittedProposalsByJobId(@Param("jobId") Long jobId);

    @Query(value = "SELECT * FROM jobs j WHERE " +
            "(:status IS NULL OR j.status = CAST(:status AS job_status)) AND " +
            "(:minBudget IS NULL OR j.budget_max >= :minBudget) AND " +
            "(:maxBudget IS NULL OR j.budget_min <= :maxBudget) " +
            "ORDER BY j.budget_max DESC",
            nativeQuery = true)
    List<Job> searchJobs(@Param("status") String status,
                         @Param("minBudget") Double minBudget,
                         @Param("maxBudget") Double maxBudget);

    @Query(value = "SELECT * FROM jobs WHERE requirements ->> :key = :value AND (:status IS NULL OR status = :status)", nativeQuery = true)
    List<Job> findByRequirementAndStatus(@Param("key") String key, @Param("value") String value, @Param("status") String status);

    @Query(value = "SELECT j.id AS jobId, j.title, j.budget_max AS budgetMax, COUNT(p.id) AS totalProposals " +
            "FROM jobs j LEFT JOIN proposals p ON p.job_id = j.id " +
            "GROUP BY j.id, j.title, j.budget_max " +
            "ORDER BY j.budget_max DESC " +
            "LIMIT :limit", nativeQuery = true)
    List<Object[]> findTopBudgetJobs(@Param("limit") int limit);


    @Query(value = """
    SELECT DISTINCT j.id
    FROM jobs j
    JOIN job_attachments a ON a.job_id = j.id
    WHERE a.expiry_date < NOW()
    """, nativeQuery = true)
    List<Long> findJobIdsWithExpiredAttachments();


    
    @Query(value = "SELECT " +
            "j.id as jobId, " +
            "j.title as title, " +
            "COALESCE(COUNT(p.id), 0) as totalProposals, " +
            "COALESCE(AVG(p.bid_amount), 0) as averageBidAmount, " +
            "COALESCE(MIN(p.bid_amount), 0) as lowestBid, " +
            "COALESCE(MAX(p.bid_amount), 0) as highestBid " +
            "FROM jobs j " +
            "LEFT JOIN proposals p ON j.id = p.job_id " +
            "WHERE j.id = :jobId " +
            "AND (:startDate IS NULL OR p.submitted_at >= CAST(:startDate AS timestamp)) " +
            "AND (:endDate IS NULL OR p.submitted_at <= CAST(:endDate AS timestamp)) " +
            "GROUP BY j.id, j.title",
            nativeQuery = true)
    List<Object[]> getProposalSummary(@Param("jobId") Long jobId,
                                      @Param("startDate") String startDate,
                                      @Param("endDate") String endDate);

        @Query(value = "SELECT job_id, status FROM contracts WHERE id = :id", nativeQuery = true)
        Optional<Object[]> findContractJobIdAndStatusById(@Param("id") Long id);
}
