package com.team26.freelance.job.event;

import com.team26.freelance.job.service.JobSearchService;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class JobIndexEventListener {

    private final JobSearchService jobSearchService;
    private final CacheManager cacheManager;

    public JobIndexEventListener(JobSearchService jobSearchService, CacheManager cacheManager) {
        this.jobSearchService = jobSearchService;
        this.cacheManager = cacheManager;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleJobIndexEvent(JobIndexEvent event) {
        if (event.isDelete()) {
            jobSearchService.removeFromIndex(event.getJobId());
        } else {
            jobSearchService.indexJob(event.getJobId(), event.getSource());
        }

        var cache = cacheManager.getCache("fullTextJobSearch");
        if (cache != null) cache.clear();
    }
}