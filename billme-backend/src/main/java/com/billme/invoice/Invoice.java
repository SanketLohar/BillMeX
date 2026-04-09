package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.merchant.MerchantProfile;
import com.billme.transaction.Transaction;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;



@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "invoices")

public class Invoice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String invoiceNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private MerchantProfile merchant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = true)
    private CustomerProfile customer;

    @Column
    private String customerEmail;

    @Column
    private String customerName;

    public String getResolvedCustomerName() {
        if (customerName != null) return customerName;
        return customer != null ? customer.getName() : "Unknown";
    }

    public String getResolvedCustomerEmail() {
        if (customerEmail != null) return customerEmail;
        return customer != null ? customer.getUser().getEmail() : "Unknown";
    }

    @Column(nullable = false)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private InvoiceStatus status;

    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod;

    @OneToMany(mappedBy = "invoice",
            cascade = CascadeType.ALL,
            orphanRemoval = true)
    private List<InvoiceItem> items = new ArrayList<>();

    @OneToOne
    @JoinColumn(name = "transaction_id")
    private Transaction transaction;

    private LocalDateTime issuedAt;
    private LocalDateTime paidAt;

    @Column
    private String razorpayOrderId;


    @PrePersist
    public void prePersist() {
        this.issuedAt = LocalDateTime.now();
        this.status = InvoiceStatus.UNPAID;
        this.invoiceNumber = "INV-" + System.currentTimeMillis();
    }

    @Column(nullable = false)
    private BigDecimal subtotal;

    @Column
    private BigDecimal cgstTotal;

    @Column
    private BigDecimal sgstTotal;

    @Column
    private BigDecimal igstTotal;

    @Column
    private BigDecimal gstTotal;

    @Column(nullable = false)
    private BigDecimal processingFee;

    @Column(nullable = false)
    private BigDecimal totalPayable;

    @Column(name = "payment_token", unique = true, nullable = false)
    private String paymentToken;

    @Column(name = "refund_window_expiry")
    private LocalDateTime refundWindowExpiry;

    @Column(name = "refund_reason", columnDefinition = "TEXT")
    private String refundReason;

    @Column(name = "refund_category", length = 32)
    private String refundCategory;

    @Column(name = "refund_status")
    private String refundStatus;

    @Column(name = "refund_requested_at")
    private LocalDateTime refundRequestedAt;

    @Column(name = "refund_processed_at")
    private LocalDateTime refundProcessedAt;

    @Column(name = "payment_started_at")
    private LocalDateTime paymentStartedAt;


    public String getPaymentToken() {
        return paymentToken;
    }

    public void setPaymentToken(String paymentToken) {
        this.paymentToken = paymentToken;
    }

    @Column(name = "payment_in_progress")
    private Boolean paymentInProgress = false;

    private LocalDate dueDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "selected_bank_account_id")
    private com.billme.merchant.MerchantBankAccount selectedBankAccount;

}