package com.billme.product;

import com.billme.merchant.MerchantProfile;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "products",
        uniqueConstraints = {
                @UniqueConstraint(columnNames = {"merchant_id", "barcode"})
        }
)
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "merchant_id", nullable = false)
    private MerchantProfile merchant;

    @Column(nullable = false)
    private String name;

    // price WITHOUT GST
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    // GST percentage
    @Column(name = "gst_rate")
    private Integer gstRate;

    private String barcode;

    @Column(nullable = false)
    private boolean active = true;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @PrePersist
    public void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    @PreUpdate
    public void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }

    public MerchantProfile getMerchant() { return merchant; }
    public void setMerchant(MerchantProfile merchant) { this.merchant = merchant; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Integer getGstRate() { return gstRate; }
    public void setGstRate(Integer gstRate) { this.gstRate = gstRate; }

    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }

    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}