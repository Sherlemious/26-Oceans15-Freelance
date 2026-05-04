package com.team26.freelance.user.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.team26.freelance.user.dto.UserContractSummaryDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ObjectArrayDtoAdapterTest {

    private final ObjectArrayDtoAdapter adapter = new ObjectArrayDtoAdapter();

    @Test
    void adaptMapsObjectArrayRowToUserContractSummaryDto() {
        Object[] row = {
                56L,
                "Youssef",
                9L,
                6L,
                2L,
                new BigDecimal("12500.75"),
                new BigDecimal("2083.46")
        };

        UserContractSummaryDTO dto = adapter.adapt(row);

        assertEquals(56L, dto.getUserId());
        assertEquals("Youssef", dto.getName());
        assertEquals(9L, dto.getTotalContracts());
        assertEquals(6L, dto.getCompletedContracts());
        assertEquals(2L, dto.getTerminatedContracts());
        assertEquals(new BigDecimal("12500.75"), dto.getTotalEarnings());
        assertEquals(new BigDecimal("2083.46"), dto.getAverageContractValue());
    }
}
