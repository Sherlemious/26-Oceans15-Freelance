package com.team26.freelance.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.common.event.PayoutAuditEvent;
import com.team26.freelance.wallet.adapter.MongoDocumentAdapter;
import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;

@Service
public class PayoutAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PayoutAnalyticsService.class);
    private static final List<String> LIFECYCLE_EVENTS = List.of(
        PayoutAuditService.COMPLETED,
        PayoutAuditService.FAILED
    );

    private final MongoTemplate mongoTemplate;
    private final MongoDocumentAdapter mongoDocumentAdapter;
    private final PayoutAnalyticsCacheService cacheService;
    private final PayoutAuditService payoutAuditService;
    private final ObjectMapper objectMapper;

    public PayoutAnalyticsService(MongoTemplate mongoTemplate,
                                  MongoDocumentAdapter mongoDocumentAdapter,
                                  PayoutAnalyticsCacheService cacheService,
                                  PayoutAuditService payoutAuditService,
                                  ObjectMapper objectMapper) {
        this.mongoTemplate = mongoTemplate;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
        this.cacheService = cacheService;
        this.payoutAuditService = payoutAuditService;
        this.objectMapper = objectMapper;
    }

    public List<PayoutMethodBreakdownDTO> getMethodBreakdown() {
        payoutAuditService.recordAnalyticsViewed();
        List<PayoutMethodBreakdownDTO> cached = readCachedBreakdown();
        if (cached != null) {
            return cached;
        }

        List<PayoutMethodBreakdownDTO> result = aggregateMethodBreakdown();
        writeCachedBreakdown(result);
        return result;
    }

    private List<PayoutMethodBreakdownDTO> readCachedBreakdown() {
        String cached = cacheService.getMethodBreakdown();
        if (cached == null || cached.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(cached, new TypeReference<>() {});
        } catch (JsonProcessingException ex) {
            log.warn("Failed to deserialize payout method breakdown cache", ex);
            cacheService.evictMethodBreakdown();
            return null;
        }
    }

    private void writeCachedBreakdown(List<PayoutMethodBreakdownDTO> result) {
        try {
            cacheService.putMethodBreakdown(objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize payout method breakdown cache", ex);
        }
    }

    private List<PayoutMethodBreakdownDTO> aggregateMethodBreakdown() {
        try {
            Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where("action").in(LIFECYCLE_EVENTS)
                    .and("method").exists(true).ne(null)
                    .and("payoutId").exists(true).ne(null)),
                Aggregation.group("payoutId")
                    .first("method").as("payoutMethod")
                    .first("amount").as("amount")
                    .max(ConditionalOperators.when(Criteria.where("action").is(PayoutAuditService.COMPLETED))
                        .then(1)
                        .otherwise(0))
                    .as("completedCount"),
                Aggregation.group("payoutMethod")
                    .sum("amount").as("totalAmount")
                    .count().as("count")
                    .sum("completedCount").as("completedCount"),
                Aggregation.project("totalAmount", "count", "completedCount")
                    .and("_id").as("payoutMethod")
                    .andExclude("_id")
            );

            AggregationResults<Document> results = mongoTemplate.aggregate(
                aggregation,
                mongoTemplate.getCollectionName(PayoutAuditEvent.class),
                Document.class
            );
            return results.getMappedResults().stream()
                .map(mongoDocumentAdapter::adapt)
                .toList();
        } catch (RuntimeException ex) {
            log.warn("Failed to aggregate payout method breakdown from MongoDB", ex);
            return List.of();
        }
    }
}
