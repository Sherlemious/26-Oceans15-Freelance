package com.team26.freelance.user.controller;

import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.service.UserSkillService;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
public class UserSkillController {

    private final UserSkillService userSkillService;

    public UserSkillController(UserSkillService userSkillService) {
        this.userSkillService = userSkillService;
    }

    @PostMapping("/api/skills/user/{userId}")
    public ResponseEntity<UserSkill> create(@PathVariable Long userId, @RequestBody UserSkill skill) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(userSkillService.create(userId, skill));
    }

    @PostMapping("/api/users/{userId}/skills")
    public ResponseEntity<UserSkill> createForUser(
            @PathVariable Long userId,
            @RequestBody UserSkill skill) {
        return ResponseEntity.status(org.springframework.http.HttpStatus.CREATED).body(userSkillService.create(userId, skill));
    }

    @GetMapping("/api/skills/{id}")
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
    public ResponseEntity<UserSkill> update(@PathVariable Long id, @RequestBody UserSkill skill) {
        return ResponseEntity.ok(userSkillService.update(id, skill));
    }

    @DeleteMapping("/api/skills/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userSkillService.delete(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/api/users/{userId}/skills/{skillId}")
    public ResponseEntity<Void> deleteUserSkill(
            @PathVariable Long userId,
            @PathVariable Long skillId) {
        userSkillService.deleteByUserSkill(userId, skillId);
        return ResponseEntity.noContent().build();
    }
}
