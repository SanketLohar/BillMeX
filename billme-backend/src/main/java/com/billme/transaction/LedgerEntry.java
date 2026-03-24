package com.billme.transaction;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "ledger_entries")
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(updatable = false, nullable = false)
    private Long id;

    @Column(updatable = false, nullable = false)
    private Long walletId;

    @Column(updatable = false, nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false, nullable = false)
    private LedgerEntryType type;

    @Column(updatable = false, nullable = false)
    private BigDecimal balanceAfter;

    @Column(updatable = false)
    private String referenceId;

    @Column(updatable = false, nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}
