package com.team26.freelance.contract.adapter;

import com.datastax.oss.driver.api.core.cql.Row;
import com.team26.freelance.contract.dto.ContractMilestoneDTO;
import org.springframework.stereotype.Component;

@Component
public class CassandraRowAdapter {

    public ContractMilestoneDTO adapt(Row row) {
        if (row == null) {
            return null;
        }
        
        ContractMilestoneDTO dto = new ContractMilestoneDTO();
        dto.setTimestamp(row.getInstant("timestamp"));
        dto.setMilestoneOrder(row.getInt("milestone_order"));
        dto.setStatus(row.getString("status"));
        dto.setRecordedBy(row.getString("recorded_by"));
        dto.setNotes(row.getString("notes"));
        
        return dto;
    }
}
