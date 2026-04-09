package com.team26.freelance.user.service;

import com.team26.freelance.user.model.User;
import com.team26.freelance.user.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public User create(User user) {
        return userRepository.save(user);
    }

    public User findById(Long id) {
        return userRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.NOT_FOUND, "User not found"));
    }

    public List<User> findAll() {
        return userRepository.findAll();
    }

    public User update(Long id, User updated) {
        User existing = findById(id);
        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setPassword(updated.getPassword());
        existing.setPhone(updated.getPhone());
        existing.setRole(updated.getRole());
        existing.setStatus(updated.getStatus());
        existing.setPreferences(updated.getPreferences());
        return userRepository.save(existing);
    }

    public void delete(Long id) {
        userRepository.deleteById(id);
    }
}