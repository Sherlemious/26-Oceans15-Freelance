package com.team26.freelance.user.repository;

import com.team26.freelance.user.model.ProposalLedger;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProposalLedgerRepository extends JpaRepository<ProposalLedger, Long> {

    Optional<ProposalLedger> findByProposalId(Long proposalId);

    boolean existsByProposalId(Long proposalId);

    void deleteByProposalId(Long proposalId);
}
