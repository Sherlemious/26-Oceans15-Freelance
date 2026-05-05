package com.team26.freelance.user.service;

import com.team26.freelance.user.config.CacheConfig;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.observer.AuthEventSubject;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserSkillService {

    public static final String USER_SKILL_CREATED = "USER_SKILL_CREATED";
    public static final String USER_SKILL_UPDATED = "USER_SKILL_UPDATED";
    public static final String USER_SKILL_DELETED = "USER_SKILL_DELETED";

    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;
    private final AuthEventSubject authEventSubject;
    private final UserCacheEvictionService userCacheEvictionService;

    public UserSkillService(UserSkillRepository userSkillRepository,
                            UserRepository userRepository,
                            AuthEventSubject authEventSubject,
                            UserCacheEvictionService userCacheEvictionService) {
        this.userSkillRepository = userSkillRepository;
        this.userRepository = userRepository;
        this.authEventSubject = authEventSubject;
        this.userCacheEvictionService = userCacheEvictionService;
    }

    public UserSkill create(Long userId, UserSkill skill) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        skill.setUser(user);
        UserSkill savedSkill = userSkillRepository.save(skill);
        recordUserSkillEvent(savedSkill, USER_SKILL_CREATED);
        userCacheEvictionService.evictUserSkillMutationCaches(savedSkill.getId(), userId);
        return savedSkill;
    }

    @Cacheable(cacheNames = CacheConfig.USER_SKILL_DETAIL_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).userSkill(#id)")
    public UserSkill findById(Long id) {
        return userSkillRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UserSkill not found"));
    }

    public List<UserSkill> findAll() {
        return userSkillRepository.findAll();
    }

    public List<UserSkill> findByUserId(Long userId) {
        if (!userRepository.existsById(userId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return userSkillRepository.findByUserId(userId);
    }

    public UserSkill update(Long id, UserSkill updated) {
        UserSkill existing = findById(id);
        existing.setSkillName(updated.getSkillName());
        existing.setCategory(updated.getCategory());
        existing.setYearsOfExperience(updated.getYearsOfExperience());
        existing.setProficiencyLevel(updated.getProficiencyLevel());
        existing.setIsPrimary(updated.getIsPrimary());
        existing.setMetadata(updated.getMetadata());
        UserSkill savedSkill = userSkillRepository.save(existing);
        recordUserSkillEvent(savedSkill, USER_SKILL_UPDATED);
        userCacheEvictionService.evictUserSkillMutationCaches(savedSkill.getId(), savedSkill.getUser().getId());
        return savedSkill;
    }

    public void delete(Long id) {
        UserSkill skill = findById(id);
        Long userId = skill.getUser().getId();
        Map<String, Object> details = userSkillDetails(skill);
        userSkillRepository.delete(skill);
        recordUserSkillEvent(userId, USER_SKILL_DELETED, details);
        userCacheEvictionService.evictUserSkillMutationCaches(id, userId);
    }

    public void deleteByUserSkill(Long userId, Long skillId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserSkill skill = findById(skillId);
        if (!skill.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill does not belong to user");
        }

        Map<String, Object> details = userSkillDetails(skill);
        userSkillRepository.delete(skill);
        recordUserSkillEvent(userId, USER_SKILL_DELETED, details);
        userCacheEvictionService.evictUserSkillMutationCaches(skillId, userId);
    }

    private void recordUserSkillEvent(UserSkill skill, String action) {
        recordUserSkillEvent(skill.getUser().getId(), action, userSkillDetails(skill));
    }

    private void recordUserSkillEvent(Long userId, String action, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("details", details == null ? Map.of() : details);
        authEventSubject.notifyObservers(action, payload);
    }

    private Map<String, Object> userSkillDetails(UserSkill skill) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("skillId", skill.getId());
        details.put("skillName", skill.getSkillName());
        details.put("category", skill.getCategory());
        details.put("yearsOfExperience", skill.getYearsOfExperience());
        details.put("proficiencyLevel", skill.getProficiencyLevel() == null ? null : skill.getProficiencyLevel().name());
        details.put("isPrimary", skill.getIsPrimary());
        return details;
    }
}
