package com.team26.freelance.contract.repository.mongo;

import com.team26.freelance.contract.model.mongo.ContractEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ContractEventRepository extends MongoRepository<ContractEvent, String> {
}
