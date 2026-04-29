package com.team26.freelance.user.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.model.AuthEvent;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.service.UserService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import java.util.Map;
import java.time.LocalDate;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final MongoTemplate mongoTemplate;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final byte[] jwtSecret;

    public UserController(UserService userService,
                          MongoTemplate mongoTemplate,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper,
                          @Value("${jwt.secret}") String jwtSecret) {
        this.userService = userService;
        this.mongoTemplate = mongoTemplate;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.jwtSecret = Decoders.BASE64.decode(jwtSecret);
    }

    @PostMapping
    public ResponseEntity<UserResponseDTO> create(@RequestBody User user) {
        
        return ResponseEntity.ok(userService.create(user));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserResponseDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userService.findById(id));
    }

    @GetMapping("/{id}/profile")
    public ResponseEntity<UserProfileDTO> getProfile(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }

    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDTO>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(userService.searchUsers(name, email, role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(userService.update(id, user));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/deactivate")
    public ResponseEntity<UserResponseDTO> deactivate(@PathVariable Long id) {
        return ResponseEntity.ok(userService.deactivate(id));
    }

    @PutMapping("/{userId}/skills/{skillId}/primary")
    public ResponseEntity<UserResponseDTO> setPrimarySkill(@PathVariable Long userId, @PathVariable Long skillId) {
        return ResponseEntity.ok(userService.setPrimarySkill(userId, skillId));
    }

    @GetMapping("/preferences/search")
    public ResponseEntity<List<UserResponseDTO>> filterByPreference(
            @RequestParam String key,
            @RequestParam String value) {
        return ResponseEntity.ok(userService.filterByPreference(key, value));
    }

    @GetMapping("/reports/top-freelancers")
    public ResponseEntity<List<TopFreelancerDTO>> topFreelancers(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam int limit) {
        return ResponseEntity.ok(userService.getTopFreelancers(startDate, endDate, limit));
    }

    @GetMapping("/preferences/language")
    public ResponseEntity<List<UserResponseDTO>> findByLanguageWithMinContracts(
            @RequestParam String lang,
            @RequestParam int minContracts) {
        return ResponseEntity.ok(userService.findByLanguageWithMinCompletedContracts(lang, minContracts));
    }
    @PutMapping("/{id}/preferences")
    public ResponseEntity<UserResponseDTO> updatePreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        return ResponseEntity.ok(userService.updatePreferences(id, preferences));
    }

    @GetMapping("/{id}/contract-summary")
    public ResponseEntity<UserContractSummaryDTO> getContractSummary(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserContractSummary(id));
    }

    @GetMapping("/{id}/activity")
    public ResponseEntity<Map<String, Object>> getActivityFeed(
            @PathVariable Long id,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "10") int size,
            @RequestHeader(value = "Authorization", required = false) String authorizationHeader) {

        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing Authorization header");
        }

        String token = authorizationHeader.startsWith("Bearer ")
                ? authorizationHeader.substring(7)
                : authorizationHeader;

        Claims claims;
        try {
            claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired token");
        }

        long callerId;
        try {
            callerId = Long.parseLong(String.valueOf(claims.get("uid")));
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token claims");
        }

        String role = String.valueOf(claims.get("role"));
        if (!"ADMIN".equals(role) && callerId != id) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        userService.findById(id);

        int resolvedPage = Math.max(page, 0);
        int resolvedSize = size <= 0 ? 10 : Math.min(size, 100);
        String cacheKey = "user-service::S1-F12::" + id + ":" + resolvedPage + ":" + resolvedSize;

        try {
            String cachedBody = redisTemplate.opsForValue().get(cacheKey);
            if (cachedBody != null) {
                Map<String, Object> cachedResponse = objectMapper.readValue(
                        cachedBody,
                        new TypeReference<Map<String, Object>>() {
                        });
                return ResponseEntity.ok(cachedResponse);
            }
        } catch (Exception ignored) {
            // Cache is an optimization; fall through to a live lookup if Redis or serialization fails.
        }

        Query countQuery = Query.query(Criteria.where("userId").is(id));
        long totalElements = mongoTemplate.count(countQuery, AuthEvent.class);

        Query eventsQuery = Query.query(Criteria.where("userId").is(id))
                .with(PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "timestamp")));
        List<AuthEvent> events = mongoTemplate.find(eventsQuery, AuthEvent.class);

        List<Map<String, Object>> content = events.stream().map(event -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("action", event.getAction());
            item.put("timestamp", event.getTimestamp());
            item.put("details", event.getDetails() != null ? event.getDetails() : Map.of());
            return item;
        }).toList();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("content", content);
        response.put("page", resolvedPage);
        response.put("size", resolvedSize);
        response.put("totalElements", totalElements);

        try {
            redisTemplate.opsForValue().set(cacheKey, objectMapper.writeValueAsString(response), Duration.ofMinutes(5));
        } catch (Exception ignored) {
            // Keep the endpoint functional even if Redis is temporarily unavailable.
        }

        return ResponseEntity.ok(response);
    }
}