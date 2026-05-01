package com.team26.freelance.contract.adapter;

import com.team26.freelance.contract.dto.ContractAnalyticsDTO;
import org.bson.Document;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class MongoDocumentAdapter {

    public ContractAnalyticsDTO adapt(Document source) {
        if (source == null) {
            source = new Document();
        }

        return ContractAnalyticsDTO.builder()
                .totalContracts(toLong(source.get("totalContracts")))
                .averageContractValue(toDouble(source.get("averageContractValue")))
                .completionRate(toDouble(source.get("completionRate")))
                .averageContractDurationDays(toDouble(source.get("averageContractDurationDays")))
                .contractsByStatus(toStatusCounts(source.get("contractsByStatus")))
                .build();
    }

    private Map<String, Long> toStatusCounts(Object value) {
        Map<String, Long> counts = new LinkedHashMap<>();
        if (!(value instanceof Map<?, ?> sourceMap)) {
            return counts;
        }
        for (Map.Entry<?, ?> entry : sourceMap.entrySet()) {
            if (entry.getKey() != null) {
                counts.put(entry.getKey().toString(), toLong(entry.getValue()));
            }
        }
        return counts;
    }

    private long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value == null) {
            return 0L;
        }
        return Long.parseLong(value.toString());
    }

    private double toDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        return Double.parseDouble(value.toString());
    }
}
