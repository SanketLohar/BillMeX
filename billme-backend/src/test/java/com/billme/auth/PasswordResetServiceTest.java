package com.billme.auth;

import com.billme.repository.PasswordResetTokenRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PasswordResetServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordResetTokenRepository tokenRepository;

    @Mock
    private PasswordResetEmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PasswordResetService passwordResetService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setPassword("oldPassword");
    }

    @Test
    void requestPasswordReset_shouldGenerateTokenAndSendEmail() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(tokenRepository.countByUserAndCreatedAtAfter(eq(testUser), any(LocalDateTime.class))).thenReturn(0);

        passwordResetService.requestPasswordReset(testUser.getEmail());

        verify(tokenRepository, times(1)).deleteByUser(testUser);
        verify(tokenRepository, times(1)).save(any(PasswordResetToken.class));
        verify(emailService, times(1)).sendPasswordResetEmail(eq(testUser.getEmail()), anyString());
    }

    @Test
    void requestPasswordReset_shouldRateLimit() {
        when(userRepository.findByEmail(testUser.getEmail())).thenReturn(Optional.of(testUser));
        when(tokenRepository.countByUserAndCreatedAtAfter(eq(testUser), any(LocalDateTime.class))).thenReturn(3);

        passwordResetService.requestPasswordReset(testUser.getEmail());

        verify(tokenRepository, never()).deleteByUser(testUser);
        verify(tokenRepository, never()).save(any(PasswordResetToken.class));
        verify(emailService, never()).sendPasswordResetEmail(anyString(), anyString());
    }

    @Test
    void resetPassword_shouldUpdatePasswordWhenValidToken() {
        String rawToken = "raw-token";
        String newPassword = "newPassword123";

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(testUser);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(10));
        token.setUsed(false);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedPassword");

        passwordResetService.resetPassword(rawToken, newPassword);

        assertEquals("encodedPassword", testUser.getPassword());
        assertTrue(token.isUsed());
        verify(userRepository, times(1)).save(testUser);
        verify(tokenRepository, times(1)).save(token);
    }

    @Test
    void resetPassword_shouldThrowExceptionWhenTokenExpired() {
        String rawToken = "raw-token";
        String newPassword = "newPassword123";

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(testUser);
        token.setExpiryDate(LocalDateTime.now().minusMinutes(1)); // Expired
        token.setUsed(false);

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(ResponseStatusException.class, () -> passwordResetService.resetPassword(rawToken, newPassword));
    }

    @Test
    void resetPassword_shouldThrowExceptionWhenTokenUsed() {
        String rawToken = "raw-token";
        String newPassword = "newPassword123";

        PasswordResetToken token = new PasswordResetToken();
        token.setUser(testUser);
        token.setExpiryDate(LocalDateTime.now().plusMinutes(10));
        token.setUsed(true); // Used

        when(tokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(token));

        assertThrows(ResponseStatusException.class, () -> passwordResetService.resetPassword(rawToken, newPassword));
    }
}
