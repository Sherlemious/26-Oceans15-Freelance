package com.team26.freelance.user.controller;

import com.team26.freelance.user.dto.UserDTO;
import com.team26.freelance.user.dto.UserContractSummaryDTO;
import com.team26.freelance.user.model.UserRole;
import com.team26.freelance.user.service.UserService;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/users")
@CrossOrigin(origins = "*")
public class UserController {
    
    @Autowired
    private UserService userService;
    
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

    /**
     * S1-F3: Get user contract summary
     * GET /api/users/{id}/contract-summary
     */
    @GetMapping("/{id}/contract-summary")
    public ResponseEntity<?> getUserContractSummary(@PathVariable Long id) {
        UserContractSummaryDTO summary = userService.getUserContractSummary(id);

        if (summary != null) {
            return ResponseEntity.ok(summary);
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body("User not found with ID: " + id);
    }
}
