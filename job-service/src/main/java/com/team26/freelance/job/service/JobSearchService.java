package com.team26.freelance.job.service;

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

    private final JobSearchRepository jobSearchRepository;
    private final JobRepository jobRepository;
    private final List<EntityObserver> observers = new ArrayList<>();
    private final ElasticsearchOperations elasticsearchOperations;

    public JobSearchService(JobSearchRepository jobSearchRepository,
                            JobRepository jobRepository,
                            JobEventRepository jobEventRepository,
                            ElasticsearchOperations elasticsearchOperations ) {
        this.jobSearchRepository = jobSearchRepository;
        this.jobRepository = jobRepository;
        this.elasticsearchOperations = elasticsearchOperations; 
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

        jobSearchRepository.save(toDocument(job));

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
        jobSearchRepository.deleteById(id);

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
     * Searches Elasticsearch using a bool query:
     *   - MUST:   multi_match on title + description (relevance-ranked)
     *   - FILTER: optional term filters (category, status)
     *             optional budget-overlap range filter
     *
     * Budget overlap: a job overlaps [minBudget, maxBudget] when
     *   job.budgetMin <= maxBudget  AND  job.budgetMax >= minBudget
     */
    public List<Job> fullTextSearch(String query,
                                    String category,
                                    String status,
                                    Double minBudget,
                                    Double maxBudget) {

        if (minBudget != null && maxBudget != null && minBudget > maxBudget) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "minBudget cannot be greater than maxBudget");
        }

        // ── 1. Build the bool query ───────────────────────────────────────────
        List<Query> filters = new ArrayList<>();

        // category filter
        if (category != null && !category.isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t.field("category").value(category.toUpperCase()))));
        }

        // status filter
        if (status != null && !status.isBlank()) {
            filters.add(Query.of(q -> q
                    .term(t -> t.field("status").value(status.toUpperCase()))));
        }

        // budget overlap:  budgetMin <= maxBudget  AND  budgetMax >= minBudget
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

        // multi_match on title + description (drives relevance score)
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

        // ── 2. Execute against Elasticsearch ─────────────────────────────────
        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(boolQuery)
                .build();

        SearchHits<JobSearchDocument> hits =
                elasticsearchOperations.search(nativeQuery, JobSearchDocument.class);

        if (hits.isEmpty()) return Collections.emptyList();

        // ── 3. Collect matching IDs (order preserved = relevance order) ───────
        List<Long> ids = hits.getSearchHits()
                             .stream()
                             .map(SearchHit::getContent)
                             .map(JobSearchDocument::getId)
                             .collect(Collectors.toList());

        // ── 4. Fetch full Job entities from PostgreSQL ────────────────────────
        Map<Long, Job> jobMap = jobRepository.findAllById(ids)
                                             .stream()
                                             .collect(Collectors.toMap(Job::getId, j -> j));

        // Re-apply ES relevance order
        return ids.stream()
                  .map(jobMap::get)
                  .filter(Objects::nonNull)
                  .collect(Collectors.toList());
    }
}