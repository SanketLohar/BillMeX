package com.billme.merchant;

import com.billme.user.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "merchant_profiles")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MerchantProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    private String businessName;
    private String ownerName;
    private String phone;
    private String address;
    private String upiId;

    private String bankName;
    private String accountHolderName;
    private String accountNumber;
    private String ifscCode;

    private boolean profileCompleted = false;

    private LocalDateTime createdAt;

    private String city;

    private String pinCode;

    @Column
    private String gstin;

    @Column
    private String state;

    @Column(nullable = false)
    private boolean gstRegistered = false;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
