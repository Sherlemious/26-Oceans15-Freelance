package com.team26.freelance.job.repository;

import com.team26.freelance.job.model.JobAttachment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobAttachmentRepository extends JpaRepository<JobAttachment, Long> {
}
