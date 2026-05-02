package com.team26.freelance.user.service;

import com.team26.freelance.user.dto.RegisterResponseDTO;
import com.team26.freelance.user.dto.LoginResponseDTO;
import com.team26.freelance.user.dto.LoginRequestDTO;
import com.team26.freelance.user.dto.RegisterRequestDTO;
import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.observer.AuthEventSubject;
import com.team26.freelance.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthEventSubject authEventSubject;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
            AuthEventSubject authEventSubject,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.authEventSubject = authEventSubject;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public RegisterResponseDTO register(RegisterRequestDTO request) {
        if (request == null
                || request.getName() == null || request.getName().isBlank()
                || request.getEmail() == null || request.getEmail().isBlank()
                || request.getPassword() == null || request.getPassword().isBlank()
                || request.getPhone() == null || request.getPhone().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "name, email, password, and phone must not be blank");
        }

        if (userRepository.existsByEmail(request.getEmail()) || userRepository.existsByPhone(request.getPhone())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "email or phone is already registered");
        }

        User user = new User();
        user.setName(request.getName());
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setPhone(request.getPhone());
        user.setRole(request.getRole() == Role.FREELANCER ? Role.FREELANCER : Role.CLIENT);
        user.setStatus(Status.ACTIVE);
        User savedUser = userRepository.save(user);

        logAuthEvent(savedUser.getId(), "REGISTERED", Map.of("email", savedUser.getEmail()));

        String token = jwtService.generateToken(savedUser);

        return new RegisterResponseDTO(token, jwtService.getExpiration());
    }

    public LoginResponseDTO login(LoginRequestDTO req) {
        User user = userRepository
                .findByEmail(req.email())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid credentials"));
        if (!passwordEncoder.matches(
                req.password(), user.getPassword())) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Invalid credentials");
        }
        logAuthEvent(user.getId(), "LOGGED_IN", Map.of("email", user.getEmail()));

        String token = jwtService.generateToken(user);
        return new LoginResponseDTO(token, user.getId(), user.getRole());
    }

    private void logAuthEvent(Long userId, String action, Map<String, Object> details) {
        authEventSubject.notifyObservers(action, Map.of(
                "userId", userId,
                "details", details));
    }
}
