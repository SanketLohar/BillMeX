package com.billme.repository;

import com.billme.customer.CustomerProfile;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CustomerProfileRepository
        extends JpaRepository<CustomerProfile, Long> {
    Optional<CustomerProfile> findByUser_Id(Long userId);

    java.util.Optional<CustomerProfile> findByUser_Email(String email);

    Optional<CustomerProfile> findByFaceEmbeddings(String faceEmbeddings);

    @org.springframework.data.jpa.repository.Query("SELECT c FROM CustomerProfile c JOIN FETCH c.user")
    java.util.List<CustomerProfile> findAllWithUser();
}
