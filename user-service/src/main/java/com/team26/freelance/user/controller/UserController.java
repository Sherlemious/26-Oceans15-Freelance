package com.team26.freelance.user.controller;

import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDate;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
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
    public ResponseEntity<UserResponseDTO> updatePreferences(@PathVariable Long id, @RequestBody JsonNode preferences) {
        return ResponseEntity.ok(userService.updatePreferences(id, preferences));
    }

    @GetMapping("/{id}/contract-summary")
    public ResponseEntity<?> getContractSummary(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(userService.getUserContractSummary(id));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("User not found with ID: " + id);
        }
    }
}
