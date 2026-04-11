package com.team26.freelance.user.service;

import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserProfileSkillDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final UserSkillRepository userSkillRepository;

    public UserService(UserRepository userRepository, UserSkillRepository userSkillRepository) {
        this.userRepository = userRepository;
        this.userSkillRepository = userSkillRepository;
    }

    public UserResponseDTO create(User user) {
        return new UserResponseDTO(userRepository.save(user));
    }

    public UserResponseDTO findById(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        return new UserResponseDTO(user);
    }

    @Transactional(readOnly = true)
    public UserProfileDTO getUserProfile(Long id) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        List<UserProfileSkillDTO> skills = user.getUserSkills().stream()
                .map(skill -> new UserProfileSkillDTO(
                        skill.getSkillName(),
                        skill.getCategory(),
                        skill.getYearsOfExperience(),
                        skill.getProficiencyLevel(),
                        skill.getIsPrimary(),
                        skill.getMetadata()))
                .collect(Collectors.toList());

        return new UserProfileDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getPhone(),
                user.getPreferences(),
                skills,
                skills.size());
    }

    public List<UserResponseDTO> findAll() {
        return userRepository.findAll().stream()
                .map(UserResponseDTO::new)
                .collect(Collectors.toList());
    }

    public UserResponseDTO update(Long id, User updated) {
        User existing = userRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setPassword(updated.getPassword());
        existing.setPhone(updated.getPhone());
        existing.setRole(updated.getRole());
        existing.setStatus(updated.getStatus());
        existing.setPreferences(updated.getPreferences());
        return new UserResponseDTO(userRepository.save(existing));
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
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
        return new UserResponseDTO(userRepository.save(user));
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> filterByPreference(String key, String value) {
        if (key.isBlank() || value.isBlank()) {
            throw new IllegalArgumentException("key and value must not be blank");
        }

        String prefJson = String.format("{\"%s\": \"%s\"}", key, value);
        return userRepository.findByPreference(prefJson).stream()
                .map(UserResponseDTO::new)
                .toList();
    }
    public List<TopFreelancerDTO> getTopFreelancers(LocalDate startDate, LocalDate endDate, int limit) {
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "startDate must be before endDate");
        }
        LocalDateTime start = startDate.atStartOfDay();
        LocalDateTime end = endDate.atTime(23, 59, 59);
        return userRepository.findTopFreelancersByEarnings(start, end, limit).stream()
                .map(row -> new TopFreelancerDTO(
                        ((Number) row[0]).longValue(),
                        (String) row[1],
                        ((Number) row[2]).doubleValue(),
                        ((Number) row[3]).longValue()))
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<UserResponseDTO> findByLanguageWithMinCompletedContracts(String lang, int minContracts) {
        if (lang == null || lang.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "lang must not be blank");
        }
        if (minContracts < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "minContracts must be >= 0");
        }

        return userRepository.findByLanguageWithMinCompletedContracts(lang, minContracts).stream()
                .map(UserResponseDTO::new)
                .collect(Collectors.toList());
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
        return new UserResponseDTO(savedUser);
    }
}
