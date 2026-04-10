package com.team26.freelance.job.repository;

import com.team26.freelance.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {
    @Query(value = "SELECT * FROM jobs j WHERE " +
            "(:status IS NULL OR j.status = :status) AND " +
            "(:minBudget IS NULL OR j.budget_max >= :minBudget) AND " +
            "(:maxBudget IS NULL OR j.budget_max <= :maxBudget) " +
            "ORDER BY j.budget_max DESC",
            nativeQuery = true)
    List<Job> searchJobs(@Param("status") String status,
                         @Param("minBudget") Double minBudget,
                         @Param("maxBudget") Double maxBudget);

    @Query(value = "SELECT * FROM jobs WHERE requirements ->> :key = :value AND (:status IS NULL OR status = :status)", nativeQuery = true)
    List<Job> findByRequirementAndStatus(@Param("key") String key, @Param("value") String value, @Param("status") String status);
}
