package com.team26.freelance.proposal.repository;

import com.team26.freelance.proposal.model.Proposal;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalRepository extends JpaRepository<Proposal, Long> {
    @Query(value = """
        SELECT COUNT(*) FROM proposals
        WHERE status IN ('SUBMITTED', 'SHORTLISTED')
          AND bid_amount BETWEEN :lowerBound AND :upperBound
        """, nativeQuery = true)
    int countActiveSimilarProposals(
            @Param("lowerBound") double lowerBound,
            @Param("upperBound") double upperBound
    );
}
