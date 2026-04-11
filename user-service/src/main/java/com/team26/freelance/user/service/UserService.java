package com.team26.freelance.user.service;

import com.team26.freelance.user.dto.UserDTO;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.model.UserRole;
import com.team26.freelance.user.repository.UserRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Optional;

@Service
public class UserService {
    
    @Autowired
    private UserRepository userRepository;
    
    /**
     * Search for users based on name, email, and role filters.
     * All filters are optional (can be null). Matching is case-insensitive.
     * Name and email support partial matching.
     * 
     * @param name Name filter (partial match, case-insensitive)
     * @param email Email filter (partial match, case-insensitive)
     * @param role Role filter (exact match)
     * @return List of users matching the filters, or empty list if no matches
     */
    public List<UserDTO> searchUsers(String name, String email, UserRole role) {
        List<User> users = userRepository.searchUsers(name, email, role);
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Create a new user
     */
    public UserDTO createUser(String name, String email, UserRole role) {
        User user = new User(name, email, role);
        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }
    
    /**
     * Create a new user with additional details
     */
    public UserDTO createUser(String name, String email, UserRole role, String phone, String bio) {
        User user = new User(name, email, role, phone, bio);
        User savedUser = userRepository.save(user);
        return convertToDTO(savedUser);
    }
    
    /**
     * Get user by ID
     */
    public Optional<UserDTO> getUserById(Long id) {
        return userRepository.findById(id)
                .map(this::convertToDTO);
    }
    
    /**
     * Get all users
     */
    public List<UserDTO> getAllUsers() {
        return userRepository.findAll().stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    /**
     * Update an existing user
     */
    public UserDTO updateUser(Long id, String name, String email, UserRole role, String phone, String bio) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            if (name != null) user.setName(name);
            if (email != null) user.setEmail(email);
            if (role != null) user.setRole(role);
            if (phone != null) user.setPhone(phone);
            if (bio != null) user.setBio(bio);
            User updatedUser = userRepository.save(user);
            return convertToDTO(updatedUser);
        }
        return null;
    }
    
    /**
     * Delete a user by ID
     */
    public boolean deleteUser(Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            return true;
        }
        return false;
    }
    
    /**
     * S1-F2: Update user preferences (JSONB)
     * Merges incoming preferences into existing preferences.
     * Overwrites existing keys, adds new ones.
     * Throws exception if user not found (returns null which controller converts to 404).
     */
    public UserDTO updatePreferences(Long id, JsonNode incomingPreferences) {
        Optional<User> userOptional = userRepository.findById(id);
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            
            // Get existing preferences or create new ObjectNode if null
            ObjectMapper mapper = new ObjectMapper();
            ObjectNode mergedPreferences;
            
            if (user.getPreferences() != null && user.getPreferences().isObject()) {
                mergedPreferences = (ObjectNode) user.getPreferences();
            } else {
                mergedPreferences = mapper.createObjectNode();
            }
            
            // Merge incoming preferences
            if (incomingPreferences != null && incomingPreferences.isObject()) {
                incomingPreferences.fields().forEachRemaining(entry -> 
                    mergedPreferences.set(entry.getKey(), entry.getValue())
                );
            }
            
            user.setPreferences(mergedPreferences);
            User updatedUser = userRepository.save(user);
            return convertToDTO(updatedUser);
        }
        return null;
    }
    
    /**
     * Convert User entity to UserDTO
     */
    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getPhone(),
                user.getBio(),
                user.getPreferences()
        );
    }
}
