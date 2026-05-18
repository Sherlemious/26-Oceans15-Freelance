package com.team26.freelance.user.service;

import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.user.logging.MdcUserScope;
import com.team26.freelance.user.model.ProposalLedger;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.repository.ProposalLedgerRepository;
import com.team26.freelance.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class UserProposalEventService {

    private static final Logger log = LoggerFactory.getLogger(UserProposalEventService.class);

    private final UserRepository userRepository;
    private final ProposalLedgerRepository proposalLedgerRepository;
    private final UserCacheEvictionService userCacheEvictionService;

    public UserProposalEventService(UserRepository userRepository,
                                   ProposalLedgerRepository proposalLedgerRepository,
                                   UserCacheEvictionService userCacheEvictionService) {
        this.userRepository = userRepository;
        this.proposalLedgerRepository = proposalLedgerRepository;
        this.userCacheEvictionService = userCacheEvictionService;
    }

    @Transactional
    public void handleProposalCompleted(ProposalCompletedEvent event) {
        validateCompletedEvent(event);

        if (proposalLedgerRepository.existsByProposalId(event.proposalId())) {
            log.info("proposal.completed ignored because ledger already exists proposalId={}",
                    event.proposalId());
            return;
        }

        Long freelancerId = event.freelancerId();
        try (MdcUserScope ignored = MdcUserScope.put(freelancerId)) {
            User user = userRepository.findById(freelancerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            BigDecimal amount = event.agreedAmount();
            long completedContracts = safeCount(user.getCompletedContracts()) + 1L;
            BigDecimal totalEarnings = safeTotal(user.getTotalEarnings()).add(amount);

            user.setCompletedContracts(completedContracts);
            user.setTotalEarnings(totalEarnings);
            userRepository.save(user);

            ProposalLedger ledger = new ProposalLedger();
            ledger.setProposalId(event.proposalId());
            ledger.setFreelancerId(freelancerId);
            ledger.setAgreedAmount(amount);
            proposalLedgerRepository.save(ledger);

            userCacheEvictionService.evictUserMutationCaches(freelancerId);
            log.info("Updated freelancer stats for proposal.completed proposalId={} userId={}",
                    event.proposalId(), freelancerId);
        }
    }

    @Transactional
    public void handleProposalCancelled(ProposalCancelledEvent event) {
        validateCancelledEvent(event);

        Optional<ProposalLedger> ledger = proposalLedgerRepository.findByProposalId(event.proposalId());
        if (ledger.isEmpty()) {
            log.info("proposal.cancelled ignored because no ledger exists proposalId={}",
                    event.proposalId());
            return;
        }

        ProposalLedger entry = ledger.get();
        Long freelancerId = entry.getFreelancerId() != null ? entry.getFreelancerId() : event.freelancerId();

        try (MdcUserScope ignored = MdcUserScope.put(freelancerId)) {
            User user = userRepository.findById(freelancerId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            BigDecimal amount = safeAmount(entry.getAgreedAmount());
            long completedContracts = Math.max(0L, safeCount(user.getCompletedContracts()) - 1L);
            BigDecimal totalEarnings = safeTotal(user.getTotalEarnings()).subtract(amount);
            if (totalEarnings.signum() < 0) {
                totalEarnings = BigDecimal.ZERO;
            }

            user.setCompletedContracts(completedContracts);
            user.setTotalEarnings(totalEarnings);
            userRepository.save(user);

            proposalLedgerRepository.deleteByProposalId(event.proposalId());
            userCacheEvictionService.evictUserMutationCaches(freelancerId);
            log.info("Reversed freelancer stats for proposal.cancelled proposalId={} userId={}",
                    event.proposalId(), freelancerId);
        }
    }

    private void validateCompletedEvent(ProposalCompletedEvent event) {
        if (event == null
                || event.proposalId() == null
                || event.freelancerId() == null
                || event.contractId() == null
                || event.agreedAmount() == null) {
            throw new IllegalArgumentException("proposal.completed event is missing required fields");
        }
        if (event.agreedAmount().signum() < 0) {
            throw new IllegalArgumentException("proposal.completed event agreedAmount must be non-negative");
        }
    }

    private void validateCancelledEvent(ProposalCancelledEvent event) {
        if (event == null || event.proposalId() == null || event.freelancerId() == null) {
            throw new IllegalArgumentException("proposal.cancelled event is missing required fields");
        }
    }

    private long safeCount(Long value) {
        return value == null ? 0L : value;
    }

    private BigDecimal safeTotal(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal safeAmount(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }
}
