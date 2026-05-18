package com.team26.freelance.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.team26.freelance.contracts.events.ProposalCancelledEvent;
import com.team26.freelance.contracts.events.ProposalCompletedEvent;
import com.team26.freelance.user.model.ProposalLedger;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.repository.ProposalLedgerRepository;
import com.team26.freelance.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class UserProposalEventServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private ProposalLedgerRepository proposalLedgerRepository;

    @Mock
    private UserCacheEvictionService userCacheEvictionService;

    private UserProposalEventService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new UserProposalEventService(userRepository, proposalLedgerRepository, userCacheEvictionService);
    }

    @Test
    void completedEventUpdatesFreelancerStatsAndCreatesLedger() {
        User user = user(7L, 0L, "0.00");
        when(proposalLedgerRepository.existsByProposalId(101L)).thenReturn(false);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.handleProposalCompleted(new ProposalCompletedEvent(
                101L, 201L, 7L, 301L, new BigDecimal("123.45")));

        assertEquals(1L, user.getCompletedContracts());
        assertEquals(new BigDecimal("123.45"), user.getTotalEarnings());
        verify(userRepository).save(user);

        ArgumentCaptor<ProposalLedger> ledgerCaptor = ArgumentCaptor.forClass(ProposalLedger.class);
        verify(proposalLedgerRepository).save(ledgerCaptor.capture());
        ProposalLedger ledger = ledgerCaptor.getValue();
        assertEquals(101L, ledger.getProposalId());
        assertEquals(7L, ledger.getFreelancerId());
        assertEquals(new BigDecimal("123.45"), ledger.getAgreedAmount());
        verify(userCacheEvictionService).evictUserMutationCaches(7L);
    }

    @Test
    void duplicateCompletedEventDoesNotApplyStatsTwice() {
        when(proposalLedgerRepository.existsByProposalId(101L)).thenReturn(true);

        service.handleProposalCompleted(new ProposalCompletedEvent(
                101L, 201L, 7L, 301L, new BigDecimal("123.45")));

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(proposalLedgerRepository, never()).save(any());
        verifyNoInteractions(userCacheEvictionService);
    }

    @Test
    void cancelledEventReversesStatsUsingLedgerAmount() {
        User user = user(7L, 1L, "123.45");
        ProposalLedger ledger = ledger(101L, 7L, "123.45");
        when(proposalLedgerRepository.findByProposalId(101L)).thenReturn(Optional.of(ledger));
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        service.handleProposalCancelled(new ProposalCancelledEvent(101L, 201L, 7L, "test"));

        assertEquals(0L, user.getCompletedContracts());
        assertEquals(new BigDecimal("0.00"), user.getTotalEarnings());
        verify(userRepository).save(user);
        verify(proposalLedgerRepository).deleteByProposalId(101L);
        verify(userCacheEvictionService).evictUserMutationCaches(7L);
    }

    @Test
    void cancelledEventWithoutLedgerPreservesStats() {
        when(proposalLedgerRepository.findByProposalId(101L)).thenReturn(Optional.empty());

        service.handleProposalCancelled(new ProposalCancelledEvent(101L, 201L, 7L, "test"));

        verify(userRepository, never()).findById(any());
        verify(userRepository, never()).save(any());
        verify(proposalLedgerRepository, never()).deleteByProposalId(any());
        verifyNoInteractions(userCacheEvictionService);
    }

    @Test
    void completedEventRejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCompleted(null));
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCompleted(
                new ProposalCompletedEvent(null, 201L, 7L, 301L, new BigDecimal("1.00"))));
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCompleted(
                new ProposalCompletedEvent(101L, 201L, null, 301L, new BigDecimal("1.00"))));
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCompleted(
                new ProposalCompletedEvent(101L, 201L, 7L, null, new BigDecimal("1.00"))));
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCompleted(
                new ProposalCompletedEvent(101L, 201L, 7L, 301L, null)));
    }

    @Test
    void completedEventRejectsNegativeAmount() {
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCompleted(
                new ProposalCompletedEvent(101L, 201L, 7L, 301L, new BigDecimal("-0.01"))));
    }

    @Test
    void cancelledEventRejectsMissingRequiredFields() {
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCancelled(null));
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCancelled(
                new ProposalCancelledEvent(null, 201L, 7L, "test")));
        assertThrows(IllegalArgumentException.class, () -> service.handleProposalCancelled(
                new ProposalCancelledEvent(101L, 201L, null, "test")));
    }

    private User user(Long id, Long completedContracts, String totalEarnings) {
        User user = new User();
        user.setId(id);
        user.setCompletedContracts(completedContracts);
        user.setTotalEarnings(new BigDecimal(totalEarnings));
        return user;
    }

    private ProposalLedger ledger(Long proposalId, Long freelancerId, String agreedAmount) {
        ProposalLedger ledger = new ProposalLedger();
        ledger.setProposalId(proposalId);
        ledger.setFreelancerId(freelancerId);
        ledger.setAgreedAmount(new BigDecimal(agreedAmount));
        return ledger;
    }
}
