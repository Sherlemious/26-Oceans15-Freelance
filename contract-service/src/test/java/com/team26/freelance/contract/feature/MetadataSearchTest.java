package com.team26.freelance.contract.feature;

import com.team26.freelance.contract.model.Contract;
import com.team26.freelance.contract.model.ContractStatus;
import com.team26.freelance.contract.service.ContractService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class MetadataSearchTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ContractService contractService;

    @Test
    void searchByMetadataGtShouldReturnContractsWithProgressAboveThreshold() throws Exception {
        Contract progress50 = buildContract(1L, 50);
        Contract progress90 = buildContract(2L, 90);

        when(contractService.searchByMetadata("progressPercentage", "gt", "40"))
                .thenReturn(List.of(progress50, progress90));

        mockMvc.perform(get("/api/contracts/metadata/search")
                        .param("key", "progressPercentage")
                        .param("operator", "gt")
                        .param("value", "40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].metadata.progressPercentage").value(50))
                .andExpect(jsonPath("$[1].metadata.progressPercentage").value(90));
    }

    @Test
    void searchByMetadataEqShouldReturnOneMatchingContract() throws Exception {
        Contract progress25 = buildContract(3L, 25);

        when(contractService.searchByMetadata("progressPercentage", "eq", "25"))
                .thenReturn(List.of(progress25));

        mockMvc.perform(get("/api/contracts/metadata/search")
                        .param("key", "progressPercentage")
                        .param("operator", "eq")
                        .param("value", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].metadata.progressPercentage").value(25));
    }

    @Test
    void searchByMetadataWithInvalidOperatorShouldReturnBadRequest() throws Exception {
        when(contractService.searchByMetadata("progressPercentage", "xyz", "50"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "operator must be one of: eq, gt, lt"));

        mockMvc.perform(get("/api/contracts/metadata/search")
                        .param("key", "progressPercentage")
                        .param("operator", "xyz")
                        .param("value", "50"))
                .andExpect(status().isBadRequest());
    }

    private Contract buildContract(Long id, int progressPercentage) {
        Contract contract = new Contract();
        contract.setId(id);
        contract.setJobId(100L + id);
        contract.setFreelancerId(200L + id);
        contract.setClientId(300L + id);
        contract.setProposalId(400L + id);
        contract.setAgreedAmount(1000.0 + id);
        contract.setStatus(ContractStatus.ACTIVE);
        contract.setStartDate(LocalDateTime.now().minusDays(2));
        contract.setEndDate(null);
        contract.setMetadata(Map.of("progressPercentage", progressPercentage));
        contract.setCreatedAt(LocalDateTime.now().minusDays(3));
        return contract;
    }
}