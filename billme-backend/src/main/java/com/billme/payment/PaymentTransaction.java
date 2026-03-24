package com.billme.payment;

import com.billme.invoice.Invoice;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_transactions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 🔗 Link to Invoice
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    // Razorpay identifiers
    @Column(name = "razorpay_payment_id", unique = true, nullable = false)
    private String razorpayPaymentId;

    @Column(name = "razorpay_order_id", nullable = false)
    private String razorpayOrderId;

    // Financial details
    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false)
    private String currency;

    @Column(nullable = false)
    private String status; // captured, failed, refunded

    @Column(name = "payment_method")
    private String paymentMethod;

    // Timestamps
    @Column(name = "captured_at")
    private LocalDateTime capturedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // Raw payload for audit/debug
    @Lob
    @Column(name = "raw_payload", columnDefinition = "TEXT")
    private String rawPayload;

    @PrePersist
    public void prePersist() {
        this.createdAt = LocalDateTime.now();
    }
}