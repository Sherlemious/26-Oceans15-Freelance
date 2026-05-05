package com.team26.freelance.user.repository;

import com.team26.freelance.common.event.AuthEvent;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AuthEventRepository extends MongoRepository<AuthEvent, String> {
    @Query(sort = "{ 'timestamp' : -1 }")
    Page<AuthEvent> findByUserId(Long userId, Pageable pageable);
}
