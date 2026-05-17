package com.team26.freelance.wallet.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.JobServiceClient;
import com.team26.freelance.contracts.feign.UserServiceClient;
import com.team26.freelance.contracts.dto.UserDTO;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class WalletReadClientServiceTest {

    @Test
    void getUserReturnsClientResponse() {
        UserServiceClient userClient = mock(UserServiceClient.class);
        UserDTO user = new UserDTO();
        user.setId(1L);
        when(userClient.getUser(1L)).thenReturn(user);

        WalletReadClientService service = serviceWith(userClient);

        assertThat(service.getUser(1L)).isSameAs(user);
    }

    @Test
    void getContractMapsNotFoundToNotFound() {
        ContractServiceClient contractClient = mock(ContractServiceClient.class);
        when(contractClient.getContract(99L)).thenThrow(feignException(404));

        WalletReadClientService service = serviceWith(contractClient);

        assertThatThrownBy(() -> service.getContract(99L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void getJobMapsFeignFailureToServiceUnavailable() {
        JobServiceClient jobClient = mock(JobServiceClient.class);
        when(jobClient.getJob(7L)).thenThrow(feignException(503));

        WalletReadClientService service = serviceWith(jobClient);

        assertThatThrownBy(() -> service.getJob(7L))
                .isInstanceOfSatisfying(
                        ResponseStatusException.class,
                        ex -> assertThat(ex.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    private static WalletReadClientService serviceWith(UserServiceClient userClient) {
        return new WalletReadClientService(
                userClient, mock(ContractServiceClient.class), mock(JobServiceClient.class));
    }

    private static WalletReadClientService serviceWith(ContractServiceClient contractClient) {
        return new WalletReadClientService(
                mock(UserServiceClient.class), contractClient, mock(JobServiceClient.class));
    }

    private static WalletReadClientService serviceWith(JobServiceClient jobClient) {
        return new WalletReadClientService(
                mock(UserServiceClient.class), mock(ContractServiceClient.class), jobClient);
    }

    private static FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/api/test",
                Collections.emptyMap(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .request(request)
                .status(status)
                .reason("status " + status)
                .headers(Collections.emptyMap())
                .build();
        return FeignException.errorStatus("test", response);
    }
}
