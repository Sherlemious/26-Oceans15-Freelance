package com.team26.freelance.wallet.repository;

import com.team26.freelance.common.event.PayoutAuditEvent;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PayoutAuditEventRepository extends MongoRepository<PayoutAuditEvent, String> {
}
