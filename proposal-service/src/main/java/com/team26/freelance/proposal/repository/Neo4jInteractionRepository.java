package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.neo4j.FreelancerNode;
import org.springframework.data.neo4j.repository.Neo4jRepository;
import org.springframework.data.neo4j.repository.query.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface Neo4jInteractionRepository extends Neo4jRepository<FreelancerNode, Long> {

        // Idempotency Check
        @Query("MATCH (f:Freelancer {userId: $freelancerId})-[r:PROPOSED_TO]->(j:Job {jobId: $jobId}) " +
                        "WHERE $proposalId IN r.recorded_proposal_ids " +
                        "RETURN count(r) > 0")
        boolean isInteractionRecorded(@Param("freelancerId") Long freelancerId, @Param("jobId") Long jobId,
                        @Param("proposalId") Long proposalId);

        // Atomic Graph Merge
        @Query("MERGE (f:Freelancer {userId: $freelancerId}) " +
                        "ON CREATE SET f.name = $freelancerName " +
                        "MERGE (j:Job {jobId: $jobId}) " +
                        "ON CREATE SET j.title = $jobTitle, j.category = $jobCategory " +
                        "MERGE (f)-[r:PROPOSED_TO]->(j) " +
                        "ON CREATE SET r.proposalCount = 1, r.lastProposalDate = datetime(), r.last_proposal_date = datetime(), r.recorded_proposal_ids = [$proposalId] "
                        +
                        "ON MATCH SET r.proposalCount = CASE WHEN NOT $proposalId IN coalesce(r.recorded_proposal_ids, []) THEN coalesce(r.proposalCount, 0) + 1 ELSE coalesce(r.proposalCount, 0) END, "
                        +
                        "r.lastProposalDate = datetime(), r.last_proposal_date = datetime(), " +
                        "r.recorded_proposal_ids = CASE WHEN NOT $proposalId IN coalesce(r.recorded_proposal_ids, []) THEN coalesce(r.recorded_proposal_ids, []) + [$proposalId] ELSE coalesce(r.recorded_proposal_ids, []) END")
        void recordInteraction(
                        @Param("freelancerId") Long freelancerId,
                        @Param("freelancerName") String freelancerName,
                        @Param("jobId") Long jobId,
                        @Param("jobTitle") String jobTitle,
                        @Param("jobCategory") String jobCategory,
                        @Param("proposalId") Long proposalId);
}