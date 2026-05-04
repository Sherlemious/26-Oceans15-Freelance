package com.team26.freelance.user.adapter;

import com.team26.freelance.user.dto.UserContractSummaryDTO;
import java.math.BigDecimal;
import org.springframework.stereotype.Component;

@Component
public class ObjectArrayDtoAdapter {

    public UserContractSummaryDTO adapt(Object[] row) {
        return UserContractSummaryDTO.builder()
                .userId(toLong(row[0]))
                .name((String) row[1])
                .totalContracts(toLong(row[2]))
                .completedContracts(toLong(row[3]))
                .terminatedContracts(toLong(row[4]))
                .totalEarnings(toBigDecimal(row[5]))
                .averageContractValue(toBigDecimal(row[6]))
                .build();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return BigDecimal.ZERO;
        }
        if (value instanceof BigDecimal bigDecimal) {
            return bigDecimal;
        }
        return new BigDecimal(value.toString());
    }
}
