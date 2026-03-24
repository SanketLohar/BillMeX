package com.billme.invoice;

import com.billme.product.Product;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Table(name = "invoice_items")
public class InvoiceItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private String productNameSnapshot;

    @Column(nullable = false)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    // GST rate for this product
    @Column(nullable = false)
    private BigDecimal gstRate;

    // GST amount for this line
    @Column(nullable = false)
    private BigDecimal gstAmount;

    // total price including GST
    @Column(nullable = false)
    private BigDecimal totalPrice;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal baseAmount;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal gstTotal;

    @Column(precision = 19, scale = 2)
    private BigDecimal cgstAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal sgstAmount;

    @Column(precision = 19, scale = 2)
    private BigDecimal igstAmount;
}