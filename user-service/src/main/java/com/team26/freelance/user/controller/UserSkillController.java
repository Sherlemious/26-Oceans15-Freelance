package com.team26.freelance.user.controller;

import com.team26.freelance.user.config.CacheEvictionService;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.service.UserSkillService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserSkillController {

    private final UserSkillService userSkillService;
    private final CacheEvictionService cacheEvictionService;

    public UserSkillController(UserSkillService userSkillService, CacheEvictionService cacheEvictionService) {
        this.userSkillService = userSkillService;
        this.cacheEvictionService = cacheEvictionService;
    }

    @PostMapping("/api/skills/user/{userId}")
    @CacheEvict(value = {"user", "user-profile", "user-search", "top-freelancers", "user-language", "user-contract-summary", "user-activity", "user-skill"}, allEntries = true)
    public ResponseEntity<UserSkill> create(@PathVariable Long userId, @RequestBody UserSkill skill) {
        cacheEvictionService.evictUserActivityFeed(userId);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(userSkillService.create(userId, skill));
    }

    @PostMapping("/api/users/{userId}/skills")
    @CacheEvict(value = {"user", "user-profile", "user-search", "top-freelancers", "user-language", "user-contract-summary", "user-activity", "user-skill"}, allEntries = true)
    public ResponseEntity<UserSkill> createForUser(
            @PathVariable Long userId,
            @RequestBody UserSkill skill) {
        cacheEvictionService.evictUserActivityFeed(userId);
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(userSkillService.create(userId, skill));
    }

    @GetMapping("/api/skills/{id}")
    @Cacheable(value = "user-skill", key = "'user-service::user-skill::' + #id")
    public ResponseEntity<UserSkill> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userSkillService.findById(id));
    }

    @GetMapping("/api/users/{userId}/skills")
    public ResponseEntity<List<UserSkill>> getByUserId(@PathVariable Long userId) {
        return ResponseEntity.ok(userSkillService.findByUserId(userId));
    }

    @GetMapping("/api/skills")
    public ResponseEntity<List<UserSkill>> getAll() {
        return ResponseEntity.ok(userSkillService.findAll());
    }

    @PutMapping("/api/skills/{id}")
    @CacheEvict(value = {"user", "user-profile", "user-search", "top-freelancers", "user-language", "user-contract-summary", "user-activity", "user-skill"}, allEntries = true)
    public ResponseEntity<UserSkill> update(@PathVariable Long id, @RequestBody UserSkill skill) {
        Long userId = userSkillService.findById(id).getUser().getId();
        cacheEvictionService.evictUserActivityFeed(userId);
        return ResponseEntity.ok(userSkillService.update(id, skill));
    }

    @DeleteMapping("/api/skills/{id}")
    @CacheEvict(value = {"user", "user-profile", "user-search", "top-freelancers", "user-language", "user-contract-summary", "user-activity", "user-skill"}, allEntries = true)
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        Long userId = userSkillService.findById(id).getUser().getId();
        cacheEvictionService.evictUserActivityFeed(userId);
        userSkillService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{userId}/skills/{skillId}")
    @CacheEvict(value = {"user", "user-profile", "user-search", "top-freelancers", "user-language", "user-contract-summary", "user-activity", "user-skill"}, allEntries = true)
    public ResponseEntity<Void> deleteUserSkill(
            @PathVariable Long userId,
            @PathVariable Long skillId) {
        cacheEvictionService.evictUserActivityFeed(userId);
        userSkillService.deleteByUserSkill(userId, skillId);
        return ResponseEntity.noContent().build();
    }
}
