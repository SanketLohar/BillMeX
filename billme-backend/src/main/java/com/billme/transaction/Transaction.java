package com.billme.transaction;

import com.billme.invoice.Invoice;
import com.billme.wallet.Wallet;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "transactions", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"external_reference", "transaction_type"})
})
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Sender Wallet
    @ManyToOne
    @JoinColumn(name = "sender_wallet_id")
    private Wallet senderWallet;

    // Receiver Wallet (nullable for UPI external)
    @ManyToOne
    @JoinColumn(name = "receiver_wallet_id")
    private Wallet receiverWallet;

    @Column(nullable = false)
    private BigDecimal amount; // Typically the total movement or customer payment

    private BigDecimal invoiceAmount;
    private BigDecimal processingFee;
    private BigDecimal merchantSettlement;

    @Enumerated(EnumType.STRING)
    @Column(name = "transaction_type")
    private TransactionType transactionType;

    @Enumerated(EnumType.STRING)
    private TransactionStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id")   // ✅ NEW
    private Invoice invoice;

    @Column(name = "external_reference")
    private String externalReference; // for UPI txn id

    private LocalDateTime createdAt;
    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }

}
