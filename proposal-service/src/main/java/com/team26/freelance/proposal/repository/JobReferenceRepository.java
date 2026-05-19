package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.JobReference;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface JobReferenceRepository extends JpaRepository<JobReference, Long> {
}
