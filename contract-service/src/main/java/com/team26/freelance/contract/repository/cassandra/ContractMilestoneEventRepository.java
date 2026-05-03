package com.team26.freelance.contract.repository.cassandra;

import com.team26.freelance.contract.model.cassandra.ContractMilestoneEvent;
import com.team26.freelance.contract.model.cassandra.ContractMilestoneEventKey;
import org.springframework.data.cassandra.repository.CassandraRepository;
import org.springframework.data.cassandra.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ContractMilestoneEventRepository extends CassandraRepository<ContractMilestoneEvent, ContractMilestoneEventKey> {

    @Query("SELECT * FROM contract_milestone_events WHERE contract_id = ?0")
    List<ContractMilestoneEvent> findByContractId(Long contractId);
}