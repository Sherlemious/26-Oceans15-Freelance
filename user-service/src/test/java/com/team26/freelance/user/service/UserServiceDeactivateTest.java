package com.team26.freelance.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.team26.freelance.contracts.events.UserDeactivatedEvent;
import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.WalletServiceClient;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.messaging.publishers.UserEventPublisher;
import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.observer.AuthEventSubject;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class UserServiceDeactivateTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private UserSkillRepository userSkillRepository;

    @Mock
    private AuthEventSubject authEventSubject;

    @Mock
    private UserCacheEvictionService userCacheEvictionService;

    @Mock
    private WalletServiceClient walletServiceClient;

    @Mock
    private ContractServiceClient contractServiceClient;

    @Mock
    private UserEventPublisher userEventPublisher;

    private UserService userService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        userService = new UserService(
                userRepository,
                userSkillRepository,
                authEventSubject,
                userCacheEvictionService,
                walletServiceClient,
                contractServiceClient,
                userEventPublisher);
    }

    @Test
    void deactivateRejectsActiveContractsAndDoesNotMutateUser() {
        User user = activeUser(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(contractServiceClient.getActiveContractCountForUser(7L)).thenReturn(1);

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.deactivate(7L));

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        assertEquals(Status.ACTIVE, user.getStatus());
        verify(userRepository, never()).save(any());
        verify(userEventPublisher, never()).publishUserDeactivated(any());
        verify(userCacheEvictionService, never()).evictUserMutationCaches(any());
    }

    @Test
    void deactivateUpdatesStatusAndPublishesEventWhenNoActiveContracts() {
        User user = activeUser(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(contractServiceClient.getActiveContractCountForUser(7L)).thenReturn(0);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        UserResponseDTO response = userService.deactivate(7L);

        assertEquals(Status.DEACTIVATED, response.getStatus());
        assertEquals(Status.DEACTIVATED, user.getStatus());
        verify(userRepository).save(user);
        verify(authEventSubject).notifyObservers(eq(UserService.USER_DEACTIVATED), any());
        verify(userCacheEvictionService).evictUserMutationCaches(7L);
        verify(userEventPublisher).publishUserDeactivated(new UserDeactivatedEvent(7L));
    }

    @Test
    void deactivateAlreadyDeactivatedUserIsNoOpAfterActiveContractCheck() {
        User user = activeUser(7L);
        user.setStatus(Status.DEACTIVATED);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(contractServiceClient.getActiveContractCountForUser(7L)).thenReturn(0);

        UserResponseDTO response = userService.deactivate(7L);

        assertEquals(Status.DEACTIVATED, response.getStatus());
        verify(userRepository, never()).save(any());
        verify(userEventPublisher, never()).publishUserDeactivated(any());
        verify(userCacheEvictionService, never()).evictUserMutationCaches(any());
    }

    @Test
    void deactivateFailsClosedWhenContractServiceIsUnavailable() {
        User user = activeUser(7L);
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));
        when(contractServiceClient.getActiveContractCountForUser(7L))
                .thenThrow(new RuntimeException("contract-service down"));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> userService.deactivate(7L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        assertEquals(Status.ACTIVE, user.getStatus());
        verify(userRepository, never()).save(any());
        verify(userEventPublisher, never()).publishUserDeactivated(any());
    }

    private User activeUser(Long id) {
        User user = new User();
        user.setId(id);
        user.setName("Freelancer");
        user.setEmail("freelancer" + id + "@example.com");
        user.setPassword("encoded");
        user.setPhone("010" + id);
        user.setRole(Role.FREELANCER);
        user.setStatus(Status.ACTIVE);
        user.setCompletedContracts(0L);
        user.setTotalEarnings(BigDecimal.ZERO);
        return user;
    }
}
