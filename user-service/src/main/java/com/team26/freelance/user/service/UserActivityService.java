package com.team26.freelance.user.service;

import com.team26.freelance.user.adapter.MongoDocumentAdapter;
import com.team26.freelance.user.config.CacheConfig;
import com.team26.freelance.user.dto.ActivityFeedResponseDTO;
import com.team26.freelance.user.dto.AuthEventDTO;
import com.team26.freelance.user.repository.UserRepository;
import java.util.List;
import org.bson.Document;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserActivityService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final String AUTH_EVENTS_COLLECTION = "auth_events";

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;
    private final MongoDocumentAdapter mongoDocumentAdapter;

    public UserActivityService(UserRepository userRepository,
                               MongoTemplate mongoTemplate,
                               MongoDocumentAdapter mongoDocumentAdapter) {
        this.userRepository = userRepository;
        this.mongoTemplate = mongoTemplate;
        this.mongoDocumentAdapter = mongoDocumentAdapter;
    }

    @Cacheable(cacheNames = CacheConfig.S1_F12_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).activityFeed(#userId, #page, #size)")
    @Transactional(readOnly = true)
    public ActivityFeedResponseDTO getActivityFeed(Long userId, int page, int size) {
        validatePagination(page, size);
        int cappedSize = Math.min(size, MAX_PAGE_SIZE);

        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }

        Criteria userCriteria = Criteria.where("userId").is(userId);
        long totalElements = mongoTemplate.count(new Query(userCriteria), AUTH_EVENTS_COLLECTION);

        Query query = new Query(Criteria.where("userId").is(userId))
                .with(Sort.by(Sort.Direction.DESC, "timestamp"))
                .skip((long) page * cappedSize)
                .limit(cappedSize);

        List<AuthEventDTO> events = mongoTemplate
                .find(query, Document.class, AUTH_EVENTS_COLLECTION)
                .stream()
                .map(mongoDocumentAdapter::adapt)
                .toList();

        return new ActivityFeedResponseDTO(events, page, cappedSize, totalElements);
    }

    private void validatePagination(int page, int size) {
        if (page < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page must be >= 0");
        }
        if (size <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "size must be > 0");
        }
    }
}
