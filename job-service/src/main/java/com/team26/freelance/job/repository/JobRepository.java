package com.team26.freelance.job.repository;

import com.team26.freelance.job.model.Job;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface JobRepository extends JpaRepository<Job, Long> {

    @Query(value = "SELECT * FROM jobs WHERE requirements ->> :key = :value AND (:status IS NULL OR status = :status)", nativeQuery = true)
    List<Job> findByRequirementAndStatus(@Param("key") String key, @Param("value") String value, @Param("status") String status);
}
