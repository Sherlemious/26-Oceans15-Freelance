package com.team26.freelance.contract.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contract.service.dto.ContractStatusUpdateRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class BatchStatusUpdateFeatureTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ContractService contractService;

    @Test
    void batchStatusUpdate_shouldReturn200WithCount_whenAllUpdatesValid() throws Exception {
        List<ContractStatusUpdateRequest> updates = List.of(
                new ContractStatusUpdateRequest(1L, ContractStatus.COMPLETED),
                new ContractStatusUpdateRequest(2L, ContractStatus.COMPLETED),
                new ContractStatusUpdateRequest(3L, ContractStatus.COMPLETED)
        );

        when(contractService.updateStatuses(anyList())).thenReturn(3);

        mockMvc.perform(put("/api/contracts/batch-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isOk())
                .andExpect(content().string("3"));
    }

    @Test
    void batchStatusUpdate_shouldReturn404_whenAnyContractIsMissing() throws Exception {
        List<ContractStatusUpdateRequest> updates = List.of(
                new ContractStatusUpdateRequest(10L, ContractStatus.COMPLETED),
                new ContractStatusUpdateRequest(11L, ContractStatus.COMPLETED)
        );

        when(contractService.updateStatuses(anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Contracts not found: [11]"));

        mockMvc.perform(put("/api/contracts/batch-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isNotFound());
    }

    @Test
    void batchStatusUpdate_shouldReturn400_whenStatusTransitionIsInvalid() throws Exception {
        List<ContractStatusUpdateRequest> updates = List.of(
                new ContractStatusUpdateRequest(20L, ContractStatus.ACTIVE)
        );

        when(contractService.updateStatuses(anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Invalid status transition for contract 20: COMPLETED -> ACTIVE"));

        mockMvc.perform(put("/api/contracts/batch-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(""));
    }

    @Test
    void batchStatusUpdate_shouldReturn400_whenRequestContainsDuplicateContractIds() throws Exception {
        List<ContractStatusUpdateRequest> updates = List.of(
                new ContractStatusUpdateRequest(5L, ContractStatus.COMPLETED),
                new ContractStatusUpdateRequest(5L, ContractStatus.TERMINATED)
        );

        when(contractService.updateStatuses(anyList()))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Duplicate contractId in request: 5"));

        mockMvc.perform(put("/api/contracts/batch-status")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updates)))
                .andExpect(status().isBadRequest());
    }
}
