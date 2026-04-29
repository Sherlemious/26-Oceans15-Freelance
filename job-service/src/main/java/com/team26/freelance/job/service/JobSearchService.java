package com.team26.freelance.job.service;

import com.team26.freelance.job.model.Job;
import com.team26.freelance.job.model.elastic.JobSearchDocument;
import com.team26.freelance.job.observer.EntityObserver;
import com.team26.freelance.job.observer.JobEventSubject;
import com.team26.freelance.job.observer.MongoEventLogger;
import com.team26.freelance.job.repository.JobRepository;
import com.team26.freelance.job.repository.elastic.JobSearchRepository;
import com.team26.freelance.job.repository.mongo.JobEventRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class JobSearchService implements JobEventSubject {

    private final JobSearchRepository jobSearchRepository;
    private final JobRepository jobRepository;
    private final List<EntityObserver> observers = new ArrayList<>();

    public JobSearchService(JobSearchRepository jobSearchRepository,
                            JobRepository jobRepository,
                            JobEventRepository jobEventRepository) {
        this.jobSearchRepository = jobSearchRepository;
        this.jobRepository = jobRepository;
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
}