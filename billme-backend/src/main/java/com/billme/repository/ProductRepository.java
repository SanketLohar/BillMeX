package com.billme.repository;

import com.billme.merchant.MerchantProfile;
import com.billme.product.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    List<Product> findByMerchantAndActiveTrue(MerchantProfile merchant);

    boolean existsByMerchantAndBarcode(MerchantProfile merchant, String barcode);
    Optional<Product> findByMerchantAndBarcode(MerchantProfile merchant, String barcode);
}