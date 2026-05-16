package com.team26.freelance.contracts.events;

public record UserRegisteredEvent(Long userId, String email, String role) {
}
