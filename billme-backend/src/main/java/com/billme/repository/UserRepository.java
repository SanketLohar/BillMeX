package com.billme.repository;

import com.billme.user.Role;
import com.billme.user.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);   // ✅ ADD THIS
    boolean existsByRole(Role role);

    long countByRole(Role role);

    @org.springframework.data.jpa.repository.Query("SELECT COUNT(u) FROM User u WHERE u.active = :active")
    long countByActive(@org.springframework.data.repository.query.Param("active") boolean active);
}
