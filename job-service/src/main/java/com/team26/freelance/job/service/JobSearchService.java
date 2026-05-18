package com.team26.freelance.job.service;

import com.team26.freelance.job.adapter.ElasticsearchHitAdapter;
import com.team26.freelance.job.dto.JobSearchResultDTO;
import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.elastic.JobSearchDocument;
import com.team26.freelance.job.observer.EntityObserver;
import com.team26.freelance.job.observer.JobEventSubject;
import com.team26.freelance.job.observer.MongoEventLogger;
import com.team26.freelance.job.repository.JobRepository;
import com.team26.freelance.job.repository.elastic.JobSearchRepository;
import com.team26.freelance.job.repository.mongo.JobEventRepository;

import co.elastic.clients.elasticsearch._types.query_dsl.Query;

import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class JobSearchService implements JobEventSubject {

    private static final Logger log = LoggerFactory.getLogger(JobSearchService.class);

    private final ObjectProvider<JobSearchRepository> jobSearchRepositoryProvider;
    private final JobRepository jobRepository;
    private final List<EntityObserver> observers = new ArrayList<>();
    private final ObjectProvider<ElasticsearchOperations> elasticsearchOperationsProvider;
    private final ElasticsearchHitAdapter elasticsearchHitAdapter;

    public JobSearchService(ObjectProvider<JobSearchRepository> jobSearchRepositoryProvider,
                            JobRepository jobRepository,
                            JobEventRepository jobEventRepository,
                            ObjectProvider<ElasticsearchOperations> elasticsearchOperationsProvider,
                            ElasticsearchHitAdapter elasticsearchHitAdapter) {
        this.jobSearchRepositoryProvider = jobSearchRepositoryProvider;
        this.jobRepository = jobRepository;
        this.elasticsearchOperationsProvider = elasticsearchOperationsProvider;
        this.elasticsearchHitAdapter = elasticsearchHitAdapter;
        // register the per-service MongoEventLogger instance
        register(new MongoEventLogger(jobEventRepository));
    }

    @Override
    public void register(EntityObserver observer) {
        observers.add(observer);
    }

    @Override
    public void unregister(EntityObserver observer) {
        observers.remove(observer);
    }

    @Override
    public void notifyObservers(String eventType, Object payload) {
        for (EntityObserver observer : observers) {
            observer.onEvent(eventType, payload);
        }
    }

    public boolean indexJob(Long id, String source) {
        Job job = jobRepository.findById(id).orElse(null);
        if (job == null) return false;

        JobSearchRepository repo = jobSearchRepositoryProvider.getIfAvailable();
        if (repo == null) {
            log.warn("Elasticsearch disabled, skipping index for jobId={} source={}", id, source);
            return false;
        }
        try {
            repo.save(toDocument(job));
        } catch (Exception ex) {
            log.warn("Elasticsearch index failed for jobId={} source={}: {}", id, source, ex.getMessage());
            return false;
        }

        List<String> indexedFields = List.of(
                "id", "title", "description",
                "category", "budgetMin", "budgetMax", "rating", "status"
        );
        notifyObservers("INDEXED", Map.of(
                "jobId", id,
                "indexedFields", indexedFields,
                "source", source
        ));

        return true;
    }

    public void removeFromIndex(Long id) {
        JobSearchRepository repo = jobSearchRepositoryProvider.getIfAvailable();
        if (repo == null) {
            log.warn("Elasticsearch disabled, skipping removeFromIndex for jobId={}", id);
            return;
        }
        try {
            repo.deleteById(id);
        } catch (Exception ex) {
            log.warn("Elasticsearch deleteById failed for jobId={}: {}", id, ex.getMessage());
            return;
        }

        notifyObservers("JOB_DELETED", Map.of(
                "jobId", id,
                "source", "auto_crud_delete"
        ));
    }

    private JobSearchDocument toDocument(Job job) {
        JobSearchDocument doc = new JobSearchDocument();
        doc.setId(job.getId());
        doc.setTitle(job.getTitle());
        doc.setDescription(job.getDescription());
        doc.setCategory(job.getCategory() != null ? job.getCategory().name() : null);
        doc.setBudgetMin(job.getBudgetMin());
        doc.setBudgetMax(job.getBudgetMax());
        doc.setRating(job.getRating());
        doc.setStatus(job.getStatus() != null ? job.getStatus().name() : null);
        return doc;
    }

    
        /**
         * Returns raw search results as DTOs from Elasticsearch (keeps ES relevance order).
         */
        public List<JobSearchResultDTO> fullTextSearchResults(String query,
                                                  String category,
                                                  String status,
                                                  Double minBudget,
                                                  Double maxBudget) {

        if (minBudget != null && maxBudget != null && minBudget > maxBudget) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "minBudget cannot be greater than maxBudget");
        }

        List<Query> filters = new ArrayList<>();

        if (category != null && !category.isBlank()) {
            filters.add(Query.of(q -> q
                .term(t -> t.field("category").value(category.toUpperCase()))));
        }

        if (status != null && !status.isBlank()) {
            filters.add(Query.of(q -> q
                .term(t -> t.field("status").value(status.toUpperCase()))));
        }

        if (minBudget != null) {
            final double min = minBudget;
            filters.add(Query.of(q -> q
                .range(r -> r.number(n -> n.field("budgetMax").gte(min)))));
        }
        if (maxBudget != null) {
            final double max = maxBudget;
            filters.add(Query.of(q -> q
                .range(r -> r.number(n -> n.field("budgetMin").lte(max)))));
        }

        final String finalQuery = query;
        Query multiMatch = Query.of(q -> q
            .multiMatch(m -> m
                .query(finalQuery)
                .fields(List.of("title", "description"))
                .type(TextQueryType.BestFields)
                .fuzziness("AUTO")));

        Query boolQuery = Query.of(q -> q
            .bool(b -> {
                b.must(multiMatch);
                if (!filters.isEmpty()) b.filter(filters);
                return b;
            }));

        NativeQuery nativeQuery = NativeQuery.builder()
            .withQuery(boolQuery)
            .build();

        ElasticsearchOperations operations = elasticsearchOperationsProvider.getIfAvailable();
        if (operations == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Full-text search is currently unavailable");
        }

        SearchHits<JobSearchDocument> hits;
        try {
            hits = operations.search(nativeQuery, JobSearchDocument.class);
        } catch (Exception ex) {
            log.warn("Elasticsearch search failed: {}", ex.getMessage());
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "Full-text search is currently unavailable");
        }

        if (hits.isEmpty()) return Collections.emptyList();

        return hits.getSearchHits()
            .stream()
            .map(elasticsearchHitAdapter::adapt)
            .collect(Collectors.toList());
        }
}