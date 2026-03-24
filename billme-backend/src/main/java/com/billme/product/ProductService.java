package com.billme.product;

import com.billme.merchant.MerchantProfile;
import com.billme.product.dto.ProductResponse;
import com.billme.repository.MerchantProfileRepository;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import com.billme.product.dto.CreateProductRequest;
import com.billme.product.dto.ProductResponse;
import java.util.List;
import com.billme.repository.ProductRepository;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request, String email) {

        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Email(email)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        if (!merchant.isProfileCompleted()) {
            throw new RuntimeException("Complete profile before adding products");
        }

        if (request.getBarcode() != null &&
                productRepository.existsByMerchantAndBarcode(merchant, request.getBarcode())) {
            throw new RuntimeException("Barcode already exists");
        }

        Product product = new Product();
        product.setMerchant(merchant);
        product.setName(request.getName());
        product.setPrice(request.getPrice());
        product.setBarcode(request.getBarcode());


        if(request.getGstRate() == null){
            throw new IllegalArgumentException("GST rate is required");
        }

        if(request.getGstRate() < 0 || request.getGstRate() > 28){
            throw new IllegalArgumentException("Invalid GST rate");
        }

        product.setGstRate(request.getGstRate());

        productRepository.save(product);

        return mapToResponse(product);
    }

    public List<ProductResponse> getProducts(String email) {

        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Email(email)
                .orElseThrow(() -> new RuntimeException("Merchant not found"));

        return productRepository.findByMerchantAndActiveTrue(merchant)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    private ProductResponse mapToResponse(Product product) {
        ProductResponse res = new ProductResponse();
        res.setId(product.getId());
        res.setName(product.getName());
        res.setPrice(product.getPrice());
        res.setGstRate(product.getGstRate());
        res.setBarcode(product.getBarcode());
        return res;
    }
    @Transactional
    public void deleteProduct(Long id, String email) {

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found"));

        // Ensure merchant owns this product
        if (!product.getMerchant().getUser().getEmail().equals(email)) {
            throw new RuntimeException("Unauthorized access");
        }

        // Soft delete
        product.setActive(false);
    }
}