package com.billme.product;

import com.billme.product.dto.CreateProductRequest;
import com.billme.product.dto.ProductResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import com.billme.product.dto.CreateProductRequest;
import com.billme.product.dto.ProductResponse;
import java.util.List;

@RestController
@RequestMapping("/api/merchant/products")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT')")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    public ResponseEntity<ProductResponse> create(
            @Valid @RequestBody CreateProductRequest request,
            Authentication authentication) {

        return ResponseEntity.ok(
                productService.createProduct(request, authentication.getName())
        );
    }

    @GetMapping
    public ResponseEntity<List<ProductResponse>> get(Authentication authentication) {
        return ResponseEntity.ok(
                productService.getProducts(authentication.getName())
        );
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            Authentication authentication) {

        productService.deleteProduct(id, authentication.getName());
        return ResponseEntity.noContent().build();
    }
}