package com.team26.freelance.contract.service;

import com.team26.freelance.contracts.dto.JobDTO;
import com.team26.freelance.contracts.dto.UserDTO;
import com.team26.freelance.contracts.feign.JobServiceClient;
import com.team26.freelance.contracts.feign.UserServiceClient;
import feign.FeignException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContractReadClientServiceTest {

    @Mock
    private UserServiceClient userServiceClient;

    @Mock
    private JobServiceClient jobServiceClient;

    @Test
    void getUserReturnsFeignResultOnSuccess() {
        ContractReadClientService service = new ContractReadClientService(userServiceClient, jobServiceClient);
        UserDTO user = new UserDTO();
        user.setId(7L);

        when(userServiceClient.getUser(7L)).thenReturn(user);

        assertSame(user, service.getUser(7L));
    }

    @Test
    void getUserMapsDownstreamNotFoundToLocalNotFound() {
        ContractReadClientService service = new ContractReadClientService(userServiceClient, jobServiceClient);
        when(userServiceClient.getUser(99L)).thenThrow(feignException(404));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getUser(99L));

        assertEquals(HttpStatus.NOT_FOUND, exception.getStatusCode());
    }

    @Test
    void getJobMapsGenericFeignFailureToServiceUnavailable() {
        ContractReadClientService service = new ContractReadClientService(userServiceClient, jobServiceClient);
        when(jobServiceClient.getJob(5L)).thenThrow(feignException(500));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.getJob(5L));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatusCode());
    }

    @Test
    void getJobReturnsFeignResultOnSuccess() {
        ContractReadClientService service = new ContractReadClientService(userServiceClient, jobServiceClient);
        JobDTO job = new JobDTO();
        job.setId(5L);

        when(jobServiceClient.getJob(5L)).thenReturn(job);

        assertSame(job, service.getJob(5L));
    }

    private FeignException feignException(int status) {
        Request request = Request.create(
                Request.HttpMethod.GET,
                "/downstream",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .request(request)
                .status(status)
                .reason("test")
                .headers(Map.of())
                .build();
        return FeignException.errorStatus("test", response);
    }
}
