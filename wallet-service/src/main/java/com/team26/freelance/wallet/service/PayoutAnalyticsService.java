package com.team26.freelance.wallet.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.wallet.adapter.MongoDocumentAdapter;
import com.team26.freelance.wallet.dto.PayoutMethodBreakdownDTO;
import com.team26.freelance.wallet.model.PayoutAuditEvent;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class PayoutAnalyticsService {

    private static final Logger log = LoggerFactory.getLogger(PayoutAnalyticsService.class);
    private static final List<String> TERMINAL_EVENTS = List.of("COMPLETED", "FAILED");

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

    public List<PayoutMethodBreakdownDTO> getMethodBreakdown(LocalDate startDate, LocalDate endDate) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startDate must not be after endDate");
        }
        payoutAuditService.recordAnalyticsViewed();

        List<PayoutMethodBreakdownDTO> cached = readCachedBreakdown(startDate, endDate);
        if (cached != null) {
            return cached;
        }

        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(LocalTime.of(23, 59, 59, 999_000_000));

        List<PayoutMethodBreakdownDTO> result = aggregateMethodBreakdown(start, end);
        writeCachedBreakdown(startDate, endDate, result);
        return result;
    }

    private List<PayoutMethodBreakdownDTO> readCachedBreakdown(LocalDate startDate, LocalDate endDate) {
        String cached = cacheService.getMethodBreakdown(startDate, endDate);
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

    private void writeCachedBreakdown(LocalDate startDate, LocalDate endDate,
                                      List<PayoutMethodBreakdownDTO> result) {
        try {
            cacheService.putMethodBreakdown(startDate, endDate, objectMapper.writeValueAsString(result));
        } catch (JsonProcessingException ex) {
            log.warn("Failed to serialize payout method breakdown cache", ex);
        }
    }

    private List<PayoutMethodBreakdownDTO> aggregateMethodBreakdown(LocalDateTime start, LocalDateTime end) {
        try {
            Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(
                    Criteria.where("eventType").in(TERMINAL_EVENTS)
                        .and("timestamp").gte(start).lte(end)
                        .and("payoutMethod").exists(true).ne(null)
                ),
                Aggregation.group("payoutMethod")
                    .sum(ConditionalOperators.when(Criteria.where("eventType").is("COMPLETED"))
                        .then(1).otherwise(0)).as("successCount")
                    .sum(ConditionalOperators.when(Criteria.where("eventType").is("FAILED"))
                        .then(1).otherwise(0)).as("failureCount")
                    .sum(ConditionalOperators.when(Criteria.where("eventType").is("COMPLETED"))
                        .thenValueOf("amount").otherwise(0)).as("totalAmount"),
                Aggregation.project("successCount", "failureCount", "totalAmount")
                    .and("_id").as("method")
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
