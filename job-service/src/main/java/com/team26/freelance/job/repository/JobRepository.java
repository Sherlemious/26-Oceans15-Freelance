package com.team26.freelance.job.repository;

import com.team26.freelance.job.dto.JobAttachmentAlertDTO;
import com.team26.freelance.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query(value = "SELECT COUNT(*) > 0 FROM contracts WHERE job_id = :jobId AND status = 'ACTIVE'", nativeQuery = true)
    boolean existsActiveContractByJobId(@Param("jobId") Long jobId);

    @Modifying
    @Query(value = "UPDATE proposals SET status = 'REJECTED' WHERE job_id = :jobId AND status = 'SUBMITTED'", nativeQuery = true)
    void rejectSubmittedProposalsByJobId(@Param("jobId") Long jobId);

    @Query(value = "SELECT * FROM jobs j WHERE " +
            "(:status IS NULL OR j.status = :status) AND " +
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
    SELECT 
        j.id AS jobId,
        j.title AS jobTitle,
        j.status AS jobStatus,
        jsonb_agg(
            jsonb_build_object(
                'id', a.id,
                'type', a.type,
                'fileUrl', a.file_url,
                'expiryDate', a.expiry_date,
                'verified', a.verified,
                'metadata', a.metadata,
                'uploadedAt', a.uploaded_at
            )
        ) FILTER (WHERE a.expiry_date < NOW()) AS expiredAttachments,
        COUNT(a.id) FILTER (WHERE a.expiry_date < NOW()) AS expiredCount
    FROM jobs j
    LEFT JOIN job_attachments a 
        ON a.job_id = j.id
    GROUP BY j.id, j.title, j.status
    HAVING COUNT(a.id) FILTER (WHERE a.expiry_date < NOW()) > 0
""", nativeQuery = true)
    List<Object[]> findExpiredAttachments();


    
}
