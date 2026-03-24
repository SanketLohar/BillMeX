package com.billme.repository;

import com.billme.auth.PasswordResetToken;
import com.billme.user.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHash(String tokenHash);

    void deleteByUser(User user);

    void deleteByExpiryDateBefore(LocalDateTime dateTime);

    int countByUserAndCreatedAtAfter(User user, LocalDateTime time);
}
