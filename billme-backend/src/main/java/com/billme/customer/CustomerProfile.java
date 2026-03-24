package com.billme.customer;

import com.billme.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "customer_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Link to main user
    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    // Basic info
    private String name;

    private String contactNumber;

    // Address fields
    private String address;

    private String state;

    private String city;

    private String pinCode;

    // Date of birth (age will be calculated from this)
    private LocalDate dob;

    // Face embeddings stored as JSON string
    @Lob
    @Column(columnDefinition = "LONGTEXT", nullable = false)
    private String faceEmbeddings;

    // Metadata
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}