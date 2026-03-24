package com.billme.auth;

import com.billme.repository.PasswordResetTokenRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordResetTokenRepository tokenRepository;
    private final PasswordResetEmailService emailService;
    private final PasswordEncoder passwordEncoder;

    private static final int MAX_REQUESTS_PER_HOUR = 3;
    private static final int TOKEN_EXPIRY_MINUTES = 15;

    @Transactional
    public void requestPasswordReset(String email) {
        // 1. Find user by email
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            // Silently return to prevent email enumeration
            log.info("Password reset requested for non-existent email: {}", email);
            return;
        }

        User user = userOpt.get();

        // 2. Token Cleanup (Delete globally expired tokens to keep table clean)
        tokenRepository.deleteByExpiryDateBefore(LocalDateTime.now());

        // 3. Rate Limiting Check
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        int requestsInLastHour = tokenRepository.countByUserAndCreatedAtAfter(user, oneHourAgo);

        if (requestsInLastHour >= MAX_REQUESTS_PER_HOUR) {
            log.warn("Rate limit exceeded for password reset user ID: {}", user.getId());
            // Silently return to pretend success but not actually send more emails
            return;
        }

        // 4. Generate raw token (UUID)
        String rawToken = UUID.randomUUID().toString();

        // 5. Delete previous unused tokens for this user
        tokenRepository.deleteByUser(user);

        // 6. Hash Token and Save
        String hashedToken = hashToken(rawToken);

        PasswordResetToken resetToken = PasswordResetToken.builder()
                .tokenHash(hashedToken)
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(TOKEN_EXPIRY_MINUTES))
                .used(false)
                .build();

        tokenRepository.save(resetToken);

        // 7. Send the email with the raw token
        emailService.sendPasswordResetEmail(user.getEmail(), rawToken);
    }

    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        // 1. Hash the incoming token
        String hashedToken = hashToken(rawToken);

        // 2. Find token in DB
        PasswordResetToken resetToken = tokenRepository.findByTokenHash(hashedToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid token"));

        // 3. Check if used
        if (resetToken.isUsed()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token already used");
        }

        // 4. Check if expired
        if (resetToken.getExpiryDate().isBefore(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token expired");
        }

        // 5. Update password
        User user = resetToken.getUser();
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // 6. Mark token as used
        resetToken.setUsed(true);
        tokenRepository.save(resetToken);
    }

    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Error hashing token", e);
        }
    }
}
