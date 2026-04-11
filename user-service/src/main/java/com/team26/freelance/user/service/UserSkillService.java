package com.team26.freelance.user.service;

import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class UserSkillService {

    private final UserSkillRepository userSkillRepository;
    private final UserRepository userRepository;

    public UserSkillService(UserSkillRepository userSkillRepository, UserRepository userRepository) {
        this.userSkillRepository = userSkillRepository;
        this.userRepository = userRepository;
    }

    public UserSkill create(Long userId, UserSkill skill) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        skill.setUser(user);
        return userSkillRepository.save(skill);
    }

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
        return userSkillRepository.save(existing);
    }

    public void delete(Long id) {
        userSkillRepository.deleteById(id);
    }

    public void deleteByUserSkill(Long userId, Long skillId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        UserSkill skill = findById(skillId);
        if (!skill.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill does not belong to user");
        }

        userSkillRepository.delete(skill);
    }
}
