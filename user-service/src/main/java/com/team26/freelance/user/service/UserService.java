package com.team26.freelance.user.service;

import com.team26.freelance.user.adapter.ObjectArrayDtoAdapter;
import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserProfileSkillDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.config.CacheConfig;
import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.observer.AuthEventSubject;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class UserService {

    public static final String PREFERENCES_UPDATED = "PREFERENCES_UPDATED";
    public static final String USER_DEACTIVATED = "USER_DEACTIVATED";
    public static final String PRIMARY_SKILL_SET = "PRIMARY_SKILL_SET";
    public static final String USER_CREATED = "USER_CREATED";
    public static final String USER_UPDATED = "USER_UPDATED";
    public static final String USER_DELETED = "USER_DELETED";
    public static final String ROLE_CHANGED = "ROLE_CHANGED";

    @Autowired
    private PasswordEncoder encoder;

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;
    private final AuthEventSubject authEventSubject;
    private final ObjectArrayDtoAdapter objectArrayDtoAdapter;
    private final UserCacheEvictionService userCacheEvictionService;

    public UserService(UserRepository userRepository,
                       UserSkillRepository userSkillRepository,
                       AuthEventSubject authEventSubject,
                       ObjectArrayDtoAdapter objectArrayDtoAdapter,
                       UserCacheEvictionService userCacheEvictionService) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.authEventSubject = authEventSubject;
        this.objectArrayDtoAdapter = objectArrayDtoAdapter;
        this.userCacheEvictionService = userCacheEvictionService;
    }

    public UserResponseDTO create(User user) {
        if (user.getEmail() == null || user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or password is required");
        }

        user.setPassword(encoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        recordUserEvent(savedUser, USER_CREATED, userDetails(savedUser));
        userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
        return UserResponseDTO.fromUser(savedUser);
    }

    @Cacheable(cacheNames = CacheConfig.USER_DETAIL_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).user(#id)")
    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponseDTO.fromUser(user);
    }

    @Cacheable(cacheNames = CacheConfig.S1_F8_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).userProfile(#id)")
    @Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserProfileSkillDTO> skills = user.getUserSkills().stream()
                .map(skill -> UserProfileSkillDTO.builder()
                        .skillName(skill.getSkillName())
                        .category(skill.getCategory())
                        .yearsOfExperience(skill.getYearsOfExperience())
                        .proficiencyLevel(skill.getProficiencyLevel())
                        .isPrimary(skill.getIsPrimary())
                        .metadata(skill.getMetadata())
                        .build())
                .collect(Collectors.toList());

        return UserProfileDTO.builder()
                .userId(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .preferences(user.getPreferences())
                .skills(skills)
                .totalSkills(skills.size())
                .build();
    }

    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream()
                .map(UserResponseDTO::fromUser)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.S1_F1_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).userSearch(#name, #email, #role)")
    @Transactional(readOnly = true)
    public List<UserResponseDTO> searchUsers(String name, String email, String role) {
        String normalizedName = normalizeFilter(name);
        String normalizedEmail = normalizeFilter(email);
        Role normalizedRole = parseRole(role);

        return userRepository.findAll().stream()
                .filter(user -> normalizedName == null || containsIgnoreCase(user.getName(), normalizedName))
                .filter(user -> normalizedEmail == null || containsIgnoreCase(user.getEmail(), normalizedEmail))
                .filter(user -> normalizedRole == null || user.getRole() == normalizedRole)
                .map(UserResponseDTO::fromUser)
                .collect(Collectors.toList());
    }

    public UserResponseDTO update(Long id, User updated) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setPassword(encoder.encode(updated.getPassword()));
        existing.setPhone(updated.getPhone());
        existing.setStatus(updated.getStatus());
        existing.setPreferences(updated.getPreferences());
        User savedUser = userRepository.save(existing);
        recordUserEvent(savedUser, USER_UPDATED, userDetails(savedUser));
        userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
        return UserResponseDTO.fromUser(savedUser);
    }

    @Transactional
    public UserResponseDTO updateRole(Long id, String requestedRole) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Role newRole = parseRequiredRole(requestedRole);
        Role oldRole = user.getRole();

        user.setRole(newRole);
        User savedUser = userRepository.save(user);

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("oldRole", oldRole == null ? null : oldRole.name());
        details.put("newRole", newRole.name());
        recordUserEvent(savedUser, ROLE_CHANGED, details);
        userCacheEvictionService.evictUserMutationCaches(id);

        return UserResponseDTO.fromUser(savedUser);
    }

    public void delete(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Map<String, Object> details = userDetails(user);
        userRepository.delete(user);
        recordUserEvent(id, USER_DELETED, details);
        userCacheEvictionService.evictUserMutationCaches(id);
    }

    @Transactional
    public UserResponseDTO deactivate(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        if (userRepository.countActiveContracts(id) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Cannot deactivate: user has active contracts");
        }
        user.setStatus(Status.DEACTIVATED);
        userRepository.withdrawSubmittedProposals(id);
        User savedUser = userRepository.save(user);
        recordUserEvent(savedUser, USER_DEACTIVATED, userDetails(savedUser));
        userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
        return UserResponseDTO.fromUser(savedUser);
    }

    @Cacheable(cacheNames = CacheConfig.S1_F5_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).preferenceSearch(#key, #value)")
    @Transactional(readOnly = true)
    public List<UserResponseDTO> filterByPreference(String key, String value) {
        if (key == null || key.isBlank() || value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "key and value must not be blank");
        }

        String prefJson = String.format("{\"%s\": \"%s\"}", key, value);
        return userRepository.findByPreference(prefJson).stream()
                .map(UserResponseDTO::fromUser)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.S1_F6_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).topFreelancers(#startDate, #endDate, #limit)")
    @Transactional(readOnly = true)
    public List<TopFreelancerDTO> getTopFreelancers(LocalDate startDate, LocalDate endDate, int limit) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before endDate");
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        return userRepository.findTopFreelancersByEarnings(start, end, limit).stream()
                .map(row -> TopFreelancerDTO.builder()
                        .userId(((Number) row[0]).longValue())
                        .name((String) row[1])
                        .totalEarnings(((Number) row[2]).doubleValue())
                        .contractCount(((Number) row[3]).longValue())
                        .build())
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.S1_F9_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).languagePreference(#lang, #minContracts)")
    @Transactional(readOnly = true)
    public List<UserResponseDTO> findByLanguageWithMinCompletedContracts(String lang, int minContracts) {
        if (lang == null || lang.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lang must not be blank");
        }
        if (minContracts < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minContracts must be >= 0");
        }

        return userRepository.findByLanguageWithMinCompletedContracts(lang, minContracts).stream()
                .map(UserResponseDTO::fromUser)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.S1_F3_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).contractSummary(#userId)")
    @Transactional(readOnly = true)
    public UserContractSummaryDTO getUserContractSummary(Long userId) {
        userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<Object[]> summaryRows = userRepository.findUserContractSummaryById(userId);
        if (summaryRows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
        return objectArrayDtoAdapter.adapt(summaryRows.get(0));
    }

    @Transactional
    public UserResponseDTO updatePreferences(Long userId, Map<String, Object> incomingPreferences) {
        if (incomingPreferences == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Invalid preferences payload: expected JSON object, got null");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Map<String, Object> merged = new HashMap<>(
                user.getPreferences() != null ? user.getPreferences() : new HashMap<>()
        );
        merged.putAll(incomingPreferences);

        user.setPreferences(merged);
        User savedUser = userRepository.save(user);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("updatedKeys", incomingPreferences.keySet());
        details.put("preferences", savedUser.getPreferences());
        recordUserEvent(savedUser, PREFERENCES_UPDATED, details);
        userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
        return UserResponseDTO.fromUser(savedUser);
    }

    @Transactional
    public UserResponseDTO setPrimarySkill(Long userId, Long skillId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserSkill targetSkill = userSkillRepository.findById(skillId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "UserSkill not found"));

        if (!targetSkill.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill does not belong to user");
        }

        for (UserSkill userSkill : user.getUserSkills()) {
            userSkill.setIsPrimary(false);
        }
        targetSkill.setIsPrimary(true);

        User savedUser = userRepository.save(user);
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("skillId", targetSkill.getId());
        details.put("skillName", targetSkill.getSkillName());
        details.put("category", targetSkill.getCategory());
        recordUserEvent(savedUser, PRIMARY_SKILL_SET, details);
        userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
        return UserResponseDTO.fromUser(savedUser);
    }

    private String normalizeFilter(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Role parseRole(String role) {
        String normalizedRole = normalizeFilter(role);
        if (normalizedRole == null) {
            return null;
        }

        try {
            return Role.valueOf(normalizedRole.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role value");
        }
    }

    private Role parseRequiredRole(String role) {
        Role parsedRole = parseRole(role);
        if (parsedRole == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid role value");
        }
        return parsedRole;
    }

    private boolean containsIgnoreCase(String source, String term) {
        return source != null && source.toLowerCase(Locale.ROOT).contains(term.toLowerCase(Locale.ROOT));
    }

    private void recordUserEvent(User user, String action, Map<String, Object> details) {
        recordUserEvent(user.getId(), action, details);
    }

    private void recordUserEvent(Long userId, String action, Map<String, Object> details) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("userId", userId);
        payload.put("details", details == null ? Map.of() : details);
        authEventSubject.notifyObservers(action, payload);
    }

    private Map<String, Object> userDetails(User user) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("email", user.getEmail());
        details.put("role", user.getRole() == null ? null : user.getRole().name());
        details.put("status", user.getStatus() == null ? null : user.getStatus().name());
        return details;
    }
}
