package com.team26.freelance.user.service;

import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.repository.UserRepository;
import com.team26.freelance.user.repository.UserSkillRepository;
import org.springframework.stereotype.Service;
import java.util.List;

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
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
        skill.setUser(user);
        return userSkillRepository.save(skill);
    }

    public UserSkill findById(Long id) {
        return userSkillRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("UserSkill not found"));
    }

    public List<UserSkill> findAll() {
        return userSkillRepository.findAll();
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
}