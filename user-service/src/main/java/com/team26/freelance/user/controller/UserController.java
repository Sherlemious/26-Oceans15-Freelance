package com.team26.freelance.user.controller;

import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UpdateRoleRequestDTO;
import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.service.UserService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;
import java.time.LocalDate;
import java.util.List;

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

    @GetMapping("/search")
    public ResponseEntity<List<UserResponseDTO>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) String role) {
        return ResponseEntity.ok(userService.searchUsers(name, email, role));
    }

    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> update(@PathVariable Long id, @RequestBody User user) {
        return ResponseEntity.ok(userService.update(id, user));
    }

    @PutMapping("/{id}/role")
    public ResponseEntity<UserResponseDTO> updateRole(@PathVariable Long id, @RequestBody UpdateRoleRequestDTO request) {
        return ResponseEntity.ok(userService.updateRole(id, request == null ? null : request.getRole()));
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
    public ResponseEntity<UserResponseDTO> updatePreferences(@PathVariable Long id, @RequestBody Map<String, Object> preferences) {
        return ResponseEntity.ok(userService.updatePreferences(id, preferences));
    }

    @GetMapping("/{id}/contract-summary")
    public ResponseEntity<UserContractSummaryDTO> getContractSummary(@PathVariable Long id) {
        return ResponseEntity.ok(userService.getUserContractSummary(id));
    }
}