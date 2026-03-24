package com.billme.product;

import com.billme.merchant.MerchantProfile;
import com.billme.product.dto.CreateProductRequest;
import com.billme.product.dto.ProductResponse;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import com.billme.user.User;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

public class ProductDiagnoseTest {

    @Mock
    ProductRepository productRepository;

    @Mock
    MerchantProfileRepository merchantProfileRepository;

    @InjectMocks
    ProductService productService;

    public ProductDiagnoseTest() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    public void test() {
        MerchantProfile merchant = new MerchantProfile();
        merchant.setProfileCompleted(true);
        when(merchantProfileRepository.findByUser_Email("test@test.com")).thenReturn(Optional.of(merchant));

        CreateProductRequest req = new CreateProductRequest();
        req.setName("Blue Pen");
        req.setPrice(new BigDecimal("20"));
        req.setBarcode("PEN999");
        req.setGstRate(18);

        ProductResponse res = productService.createProduct(req, "test@test.com");
        
        System.out.println("Response GST: " + res.getGstRate());
        
        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        
        System.out.println("Saved Product GST: " + captor.getValue().getGstRate());
    }
}
