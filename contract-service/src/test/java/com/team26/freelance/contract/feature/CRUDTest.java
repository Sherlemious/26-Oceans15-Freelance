package com.team26.freelance.contract.feature;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import com.team26.freelance.contract.service.dto.ContractStatusUpdateRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
public class CRUDTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContractService contractService;

    @Autowired
    private ObjectMapper objectMapper;

    private Contract contract;

    @BeforeEach
    void setUp() {
        contract = new Contract();
        contract.setId(1L);
        contract.setJobId(100L);
        contract.setFreelancerId(200L);
        contract.setClientId(300L);
        contract.setProposalId(400L);
        contract.setAgreedAmount(2500.0);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDateTime.now());
    }

    @Test
    void create_ShouldReturn201Created() throws Exception {
        when(contractService.create(any(Contract.class))).thenReturn(contract);

        mockMvc.perform(post("/api/contracts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contract)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void findById_ShouldReturn200Ok() throws Exception {
        when(contractService.findById(1L)).thenReturn(contract);

        mockMvc.perform(get("/api/contracts/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void findAll_ShouldReturn200Ok() throws Exception {
        when(contractService.findAll()).thenReturn(Collections.singletonList(contract));

        mockMvc.perform(get("/api/contracts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1L));
    }

    @Test
    void update_ShouldReturn200Ok() throws Exception {
        when(contractService.update(eq(1L), any(Contract.class))).thenReturn(contract);

        mockMvc.perform(put("/api/contracts/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(contract)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1L));
    }

    @Test
    void delete_ShouldReturn204NoContent() throws Exception {
        mockMvc.perform(delete("/api/contracts/1"))
                .andExpect(status().isNoContent());
    }
}
