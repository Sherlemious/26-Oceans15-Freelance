package com.team26.freelance.user.controller;

import com.team26.freelance.user.model.UserSkill;
import com.team26.freelance.user.service.UserSkillService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/skills")
public class UserSkillController {

    private final UserSkillService userSkillService;

    public UserSkillController(UserSkillService userSkillService) {
        this.userSkillService = userSkillService;
    }

    @PostMapping("/user/{userId}")
    public ResponseEntity<UserSkill> create(@PathVariable Long userId, @RequestBody UserSkill skill) {
        return ResponseEntity.ok(userSkillService.create(userId, skill));
    }

    @GetMapping("/{id}")
    public ResponseEntity<UserSkill> getById(@PathVariable Long id) {
        return ResponseEntity.ok(userSkillService.findById(id));
    }

    @GetMapping
    public ResponseEntity<List<UserSkill>> getAll() {
        return ResponseEntity.ok(userSkillService.findAll());
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserSkill> update(@PathVariable Long id, @RequestBody UserSkill skill) {
        return ResponseEntity.ok(userSkillService.update(id, skill));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        userSkillService.delete(id);
        return ResponseEntity.noContent().build();
    }
}