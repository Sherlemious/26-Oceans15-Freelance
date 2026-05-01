package com.team26.freelance.user.service;

import com.team26.freelance.user.dto.AuthResponseDTO;
import com.team26.freelance.user.dto.LoginRequestDTO;
import com.team26.freelance.user.dto.RegisterRequestDTO;
import com.team26.freelance.user.model.AuthEvent;
import com.team26.freelance.user.model.EventFactory;
import com.team26.freelance.user.model.EventType;
import com.team26.freelance.user.model.Role;
import com.team26.freelance.user.model.Status;
import com.team26.freelance.user.model.User;
import com.team26.freelance.user.repository.AuthEventRepository;
import com.team26.freelance.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final AuthEventRepository authEventRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UserRepository userRepository,
            AuthEventRepository authEventRepository,
            PasswordEncoder passwordEncoder,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.authEventRepository = authEventRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponseDTO register(RegisterRequestDTO request) {
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

        Map<String, Object> registerParams = new HashMap<>();
        registerParams.put("userId", savedUser.getId());
        registerParams.put("action", "REGISTERED");
        registerParams.put("details", Map.of("email", savedUser.getEmail()));
        authEventRepository.save((AuthEvent) EventFactory.createEvent(EventType.AUTH, registerParams));

        String token = jwtService.generateToken(savedUser);

        return new AuthResponseDTO(token, savedUser.getId(), savedUser.getRole());
    }

    public AuthResponseDTO login(LoginRequestDTO req) {
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
        Map<String, Object> loginParams = new HashMap<>();
        loginParams.put("userId", user.getId());
        loginParams.put("action", "LOGGED_IN");
        loginParams.put("details", Map.of("email", user.getEmail()));
        authEventRepository.save((AuthEvent) EventFactory.createEvent(EventType.AUTH, loginParams));

        String token = jwtService.generateToken(user);
        return new AuthResponseDTO(token, user.getId(), user.getRole());
    }
}
