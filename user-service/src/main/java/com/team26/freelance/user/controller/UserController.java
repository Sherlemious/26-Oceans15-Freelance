package com.team26.freelance.user.controller;

import com.team26.freelance.user.dto.TopFreelancerDTO;
import com.team26.freelance.user.dto.UserProfileDTO;
import com.team26.freelance.user.dto.UserResponseDTO;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.format.annotation.DateTimeFormat;
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
    
    /**
     * S1-F1: Search users with filters
     * GET /api/users/search?name={name}&email={email}&role={role}
     * 
     * All filters are optional. Returns list of users matching any non-null filter.
     * Name and email support partial matching (case insensitive).
     */
    @GetMapping("/search")
    public ResponseEntity<List<UserDTO>> searchUsers(
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String email,
            @RequestParam(required = false) UserRole role) {
        
        List<UserDTO> users = userService.searchUsers(name, email, role);
        return ResponseEntity.ok(users);
    }
    
    /**
     * Create a new user (CRUD)
     */
    @PostMapping
    public ResponseEntity<UserDTO> createUser(@RequestBody UserDTO userDTO) {
        UserDTO createdUser = userService.createUser(
                userDTO.getName(),
                userDTO.getEmail(),
                userDTO.getRole(),
                userDTO.getPhone(),
                userDTO.getBio()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(createdUser);
    }
    
    /**
     * Get all users (CRUD)
     */
    @GetMapping
    public ResponseEntity<List<UserDTO>> getAllUsers() {
        List<UserDTO> users = userService.getAllUsers();
        return ResponseEntity.ok(users);
    }
    
    /**
     * Get user by ID (CRUD)
     */
    @GetMapping("/{id}")
    public ResponseEntity<?> getUserById(@PathVariable Long id) {
        Optional<UserDTO> user = userService.getUserById(id);
        if (user.isPresent()) {
            return ResponseEntity.ok(user.get());
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("User not found with ID: " + id);
    }
    
    /**
     * Update a user (CRUD)
     */
    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody UserDTO userDTO) {
        UserDTO updatedUser = userService.updateUser(
                id,
                userDTO.getName(),
                userDTO.getEmail(),
                userDTO.getRole(),
                userDTO.getPhone(),
                userDTO.getBio()
        );
        
        if (updatedUser != null) {
            return ResponseEntity.ok(updatedUser);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("User not found with ID: " + id);
    }
    
    /**
     * Delete a user (CRUD)
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        boolean deleted = userService.deleteUser(id);
        if (deleted) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("User not found with ID: " + id);
    }
    
    /**
     * S1-F2: Update user preferences (JSONB)
     * PUT /api/users/{id}/preferences
     * 
     * Merges incoming preferences into existing preferences.
     * Overwrites existing keys, adds new ones.
     * Returns 404 if user not found.
     */
    @PutMapping("/{id}/preferences")
    public ResponseEntity<?> updatePreferences(@PathVariable Long id, @RequestBody JsonNode preferences) {
        UserDTO updatedUser = userService.updatePreferences(id, preferences);
        
        if (updatedUser != null) {
            return ResponseEntity.ok(updatedUser);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("User not found with ID: " + id);
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

    @PutMapping("/{id}/preferences")
    public ResponseEntity<UserResponseDTO> updatePreferences(@PathVariable Long id, @RequestBody JsonNode preferences) {
        return ResponseEntity.ok(userService.updatePreferences(id, preferences));
    }
}
