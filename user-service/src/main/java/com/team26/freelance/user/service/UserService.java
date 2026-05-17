package com.team26.freelance.user.service;

import com.team26.freelance.contracts.dto.UserDTO;
import com.team26.freelance.contracts.feign.ContractServiceClient;
import com.team26.freelance.contracts.feign.WalletServiceClient;
import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserProfileSkillDTO;
import com.team26.freelance.user.dto.UserSkillResponseDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.config.CacheConfig;
import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.logging.MdcUserScope;
import com.team26.freelance.user.observer.AuthEventSubject;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;

import feign.FeignException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;


@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

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
    private final UserCacheEvictionService userCacheEvictionService;
    private final WalletServiceClient walletServiceClient;
    private final ContractServiceClient contractServiceClient;

    public UserService(UserRepository userRepository,
                       UserSkillRepository userSkillRepository,
                       AuthEventSubject authEventSubject,
                       UserCacheEvictionService userCacheEvictionService,
                       WalletServiceClient walletServiceClient,
                       ContractServiceClient contractServiceClient) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
        this.authEventSubject = authEventSubject;
        this.userCacheEvictionService = userCacheEvictionService;
        this.walletServiceClient = walletServiceClient;
        this.contractServiceClient = contractServiceClient;
    }

    public UserResponseDTO create(User user) {
        if (user.getEmail() == null || user.getPassword() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email or password is required");
        }

        user.setPassword(encoder.encode(user.getPassword()));
        User savedUser = userRepository.save(user);
        try (MdcUserScope ignored = MdcUserScope.put(savedUser.getId())) {
            log.info("User {} saved with status={}", savedUser.getId(), savedUser.getStatus());
            recordUserEvent(savedUser, USER_CREATED, userDetails(savedUser));
            userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
            return UserResponseDTO.fromUser(savedUser);
        }
    }

    @Cacheable(cacheNames = CacheConfig.USER_DETAIL_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).user(#id)")
    @Transactional(readOnly = true)
    public UserResponseDTO findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return UserResponseDTO.fromUser(user);
    }

    @Transactional(readOnly = true)
    public UserDTO findProviderUserById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return toProviderUserDto(user);
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
        try (MdcUserScope ignored = MdcUserScope.put(id)) {
            User existing = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            existing.setName(updated.getName());
            existing.setEmail(updated.getEmail());
            existing.setPassword(encoder.encode(updated.getPassword()));
            existing.setPhone(updated.getPhone());
            existing.setStatus(updated.getStatus());
            existing.setPreferences(updated.getPreferences());
            User savedUser = userRepository.save(existing);
            log.info("User {} saved with status={}", savedUser.getId(), savedUser.getStatus());
            recordUserEvent(savedUser, USER_UPDATED, userDetails(savedUser));
            userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
            return UserResponseDTO.fromUser(savedUser);
        }
    }

    @Transactional
    public UserResponseDTO updateRole(Long id, String requestedRole) {
        try (MdcUserScope ignored = MdcUserScope.put(id)) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

            Role newRole = parseRequiredRole(requestedRole);
            Role oldRole = user.getRole();

            user.setRole(newRole);
            User savedUser = userRepository.save(user);

            Map<String, Object> details = new LinkedHashMap<>();
            details.put("oldRole", oldRole == null ? null : oldRole.name());
            details.put("newRole", newRole.name());
            log.info("User {} role changed from {} to {}", id, oldRole, newRole);
            recordUserEvent(savedUser, ROLE_CHANGED, details);
            userCacheEvictionService.evictUserMutationCaches(id);

            return UserResponseDTO.fromUser(savedUser);
        }
    }

    public void delete(Long id) {
        try (MdcUserScope ignored = MdcUserScope.put(id)) {
            User user = userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            Map<String, Object> details = userDetails(user);
            userRepository.delete(user);
            log.info("User {} deleted", id);
            recordUserEvent(id, USER_DELETED, details);
            userCacheEvictionService.evictUserMutationCaches(id);
        }
    }

    @Transactional
    public UserResponseDTO deactivate(Long id) {
        try (MdcUserScope ignored = MdcUserScope.put(id)) {
            userRepository.findById(id)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
            throw feignRequired("User deactivation requires contract-service active contract checks");
        }
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
                    "startDate must be before or equal to endDate");
        }
        if (limit <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "limit must be > 0");
        }

        long startedAt = System.nanoTime();
        try {
            String startDateParam = startDate.toString();
            String endDateParam = endDate.toString();

            return userRepository.findAll().stream()
                    .filter(user -> user.getRole() == Role.FREELANCER)
                    .map(freelancer -> topFreelancer(freelancer, startDateParam, endDateParam))
                    .sorted(Comparator
                            .comparing(TopFreelancerDTO::getTotalEarnings,
                                    Comparator.nullsFirst(Comparator.naturalOrder()))
                            .reversed()
                            .thenComparing(TopFreelancerDTO::getUserId))
                    .limit(limit)
                    .collect(Collectors.toList());
        } finally {
            long elapsedMs = (System.nanoTime() - startedAt) / 1_000_000;
            if (elapsedMs > 1000) {
                log.warn("Slow S1-F6 top-freelancers report took {}ms for startDate={} endDate={} limit={}",
                        elapsedMs, startDate, endDate, limit);
            }
        }
    }

    private TopFreelancerDTO topFreelancer(User freelancer, String startDate, String endDate) {
        Long freelancerId = freelancer.getId();
        BigDecimal totalEarnings = getFreelancerPayoutTotal(freelancerId, startDate, endDate);
        long contractCount = getCompletedContractCount(freelancerId);

        return TopFreelancerDTO.builder()
                .userId(freelancerId)
                .name(freelancer.getName())
                .totalEarnings(totalEarnings.doubleValue())
                .contractCount(contractCount)
                .build();
    }

    private BigDecimal getFreelancerPayoutTotal(Long freelancerId, String startDate, String endDate) {
        log.info("Calling WalletServiceClient.getFreelancerPayoutTotal freelancerId={} startDate={} endDate={}",
                freelancerId, startDate, endDate);
        try {
            BigDecimal total = walletServiceClient.getFreelancerPayoutTotal(freelancerId, startDate, endDate);
            log.info("WalletServiceClient.getFreelancerPayoutTotal returned successfully for freelancerId={}",
                    freelancerId);
            return total == null ? BigDecimal.ZERO : total;
        } catch (FeignException.NotFound ex) {
            log.warn("WalletServiceClient.getFreelancerPayoutTotal returned 404 for freelancerId={}",
                    freelancerId);
            return BigDecimal.ZERO;
        } catch (FeignException ex) {
            log.warn("WalletServiceClient.getFreelancerPayoutTotal failed for freelancerId={} status={}",
                    freelancerId, ex.status(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Wallet service temporarily unavailable", ex);
        } catch (RuntimeException ex) {
            log.warn("WalletServiceClient.getFreelancerPayoutTotal failed for freelancerId={}",
                    freelancerId, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Wallet service temporarily unavailable", ex);
        }
    }

    private long getCompletedContractCount(Long freelancerId) {
        log.info("Calling ContractServiceClient.getCompletedContractCountForUser userId={}", freelancerId);
        try {
            long count = contractServiceClient.getCompletedContractCountForUser(freelancerId);
            log.info("ContractServiceClient.getCompletedContractCountForUser returned successfully for userId={}",
                    freelancerId);
            return count;
        } catch (FeignException.NotFound ex) {
            log.warn("ContractServiceClient.getCompletedContractCountForUser returned 404 for userId={}",
                    freelancerId);
            return 0L;
        } catch (FeignException ex) {
            log.warn("ContractServiceClient.getCompletedContractCountForUser failed for userId={} status={}",
                    freelancerId, ex.status(), ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Contract service temporarily unavailable", ex);
        } catch (RuntimeException ex) {
            log.warn("ContractServiceClient.getCompletedContractCountForUser failed for userId={}",
                    freelancerId, ex);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Contract service temporarily unavailable", ex);
        }
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
        String normalizedLang = lang.trim();
        List<User> candidates = userRepository.findByPreferredLanguage(normalizedLang);

        return candidates.stream()
                .filter(user -> getCompletedContractCount(user.getId()) >= minContracts)
                .map(UserResponseDTO::fromUser)
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = CacheConfig.S1_F3_CACHE,
            key = "T(com.team26.freelance.user.service.UserCacheKeys).contractSummary(#userId)")
    @Transactional(readOnly = true)
    public UserContractSummaryDTO getUserContractSummary(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        try {
            com.team26.freelance.contracts.dto.UserContractSummaryDTO summary =
                    contractServiceClient.getUserContractSummary(userId);
            return UserContractSummaryDTO.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .totalContracts(summary == null || summary.getTotalContracts() == null ? 0L : summary.getTotalContracts())
                    .completedContracts(summary == null || summary.getCompletedContracts() == null ? 0L : summary.getCompletedContracts())
                    .terminatedContracts(summary == null || summary.getTerminatedContracts() == null ? 0L : summary.getTerminatedContracts())
                    .totalEarnings(summary == null || summary.getTotalEarnings() == null ? BigDecimal.ZERO : summary.getTotalEarnings())
                    .averageContractValue(summary == null || summary.getAverageContractValue() == null
                            ? BigDecimal.ZERO : summary.getAverageContractValue())
                    .build();
        } catch (FeignException.NotFound ex) {
            return UserContractSummaryDTO.builder()
                    .userId(user.getId())
                    .name(user.getName())
                    .totalContracts(0L)
                    .completedContracts(0L)
                    .terminatedContracts(0L)
                    .totalEarnings(BigDecimal.ZERO)
                    .averageContractValue(BigDecimal.ZERO)
                    .build();
        } catch (FeignException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Contract service temporarily unavailable", ex);
        } catch (RuntimeException ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Contract service temporarily unavailable", ex);
        }
    }

    @Transactional
    public UserResponseDTO updatePreferences(Long userId, Map<String, Object> incomingPreferences) {
        try (MdcUserScope ignored = MdcUserScope.put(userId)) {
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
            log.info("User {} preferences updated", userId);
            recordUserEvent(savedUser, PREFERENCES_UPDATED, details);
            userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
            return UserResponseDTO.fromUser(savedUser);
        }
    }

    @Transactional
    public UserResponseDTO setPrimarySkill(Long userId, Long skillId) {
        try (MdcUserScope ignored = MdcUserScope.put(userId)) {
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
            log.info("User {} primary skill set to {}", userId, skillId);
            recordUserEvent(savedUser, PRIMARY_SKILL_SET, details);
            userCacheEvictionService.evictUserMutationCaches(savedUser.getId());
            return UserResponseDTO.fromUser(savedUser);
        }
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

    private ResponseStatusException feignRequired(String reason) {
        log.warn("Feign call to downstream service failed: {}", reason);
        return new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, reason);
    }

    private UserDTO toProviderUserDto(User user) {
        UserDTO dto = new UserDTO();
        dto.setId(user.getId());
        dto.setName(user.getName());
        dto.setEmail(user.getEmail());
        dto.setPhone(user.getPhone());
        dto.setRole(user.getRole() == null ? null : user.getRole().name());
        dto.setStatus(user.getStatus() == null ? null : user.getStatus().name());
        dto.setPreferences(user.getPreferences());
        dto.setCreatedAt(user.getCreatedAt());
        dto.setUserSkills(user.getUserSkills().stream()
                .map(skill -> (Object) toProviderUserSkillDto(skill))
                .collect(Collectors.toList()));
        return dto;
    }

    private static UserSkillResponseDTO toProviderUserSkillDto(UserSkill skill) {
        return UserSkillResponseDTO.builder()
                .id(skill.getId())
                .skillName(skill.getSkillName())
                .category(skill.getCategory())
                .yearsOfExperience(skill.getYearsOfExperience())
                .proficiencyLevel(skill.getProficiencyLevel())
                .isPrimary(skill.getIsPrimary())
                .metadata(skill.getMetadata())
                .createdAt(skill.getCreatedAt())
                .build();
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
