package com.billme.repository;

import com.billme.wallet.Wallet;
import com.billme.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.Optional;

public interface WalletRepository extends JpaRepository<Wallet, Long> {

    // Fetch by User entity
    Optional<Wallet> findByUser(User user);

    // 🔥 Recommended for services
    Optional<Wallet> findByUser_Id(Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.user.id = :userId")
    Optional<Wallet> findByUserWithLock(@Param("userId") Long userId);
}
