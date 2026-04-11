package com.team26.freelance.job.repository;

import com.team26.freelance.job.model.JobAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.query.Param;

@Repository
public interface JobAttachmentRepository extends JpaRepository<JobAttachment, Long> {
    List<JobAttachment> findByJobId(Long jobId);


    @Query("SELECT a FROM JobAttachment a JOIN FETCH a.job WHERE a.id = :id")
    Optional<JobAttachment> findByIdWithJob(Long id);

    @Query(value = "SELECT role FROM users WHERE id = :id", nativeQuery = true)
    Optional<String> findUserRoleById(@Param("id") Long id);
}
