package com.mygamelist.api.service;

import java.time.Clock;
import java.util.Locale;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mygamelist.api.entity.UserAccount;
import com.mygamelist.api.repository.UserAccountRepository;
import com.mygamelist.api.service.exception.ResourceConflictException;
import com.mygamelist.api.service.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class UserAccountService {

    private final UserAccountRepository userAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public UserAccountService(UserAccountRepository userAccountRepository, PasswordEncoder passwordEncoder, Clock clock) {
        this.userAccountRepository = userAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public UserAccount register(String username, String email, String rawPassword) {
        String normalizedUsername = normalizeIdentifier(username, "username");
        String normalizedEmail = normalizeIdentifier(email, "email");

        if (userAccountRepository.existsByUsername(normalizedUsername)) {
            throw new ResourceConflictException("Username is already in use");
        }

        if (userAccountRepository.existsByEmail(normalizedEmail)) {
            throw new ResourceConflictException("Email is already in use");
        }

        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("rawPassword must not be blank");
        }

        String passwordHash = passwordEncoder.encode(rawPassword);
        UserAccount user = new UserAccount(normalizedUsername, normalizedEmail, passwordHash, clock.instant());
        return userAccountRepository.save(user);
    }

    public UserAccount findByUsername(String username) {
        String normalizedUsername = normalizeIdentifier(username, "username");
        return userAccountRepository.findByUsername(normalizedUsername)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + normalizedUsername));
    }

    @Transactional
    public UserAccount updateBio(UUID userId, String bio) {
        UserAccount user = findById(userId);
        user.updateBio(bio, clock.instant());
        return user;
    }

    private UserAccount findById(UUID userId) {
        return userAccountRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));
    }

    private static String normalizeIdentifier(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }

        return value.trim().toLowerCase(Locale.ROOT);
    }
}
