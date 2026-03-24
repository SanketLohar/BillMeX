package com.billme.product;

import com.billme.merchant.MerchantProfile;
import com.billme.product.Product;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.ProductRepository;
import com.billme.user.User;
import com.billme.user.Role;
import com.billme.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import java.math.BigDecimal;

@SpringBootTest
@ActiveProfiles("test")
public class PersistenceDiagnoseTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private MerchantProfileRepository merchantProfileRepository;
    
    @Autowired
    private UserRepository userRepository;

    @Test
    public void testPersistence() {
        User user = new User();
        user.setEmail("diag@test.com");
        user.setPassword("pass");
        user.setRole(Role.MERCHANT);
        userRepository.save(user);
        
        MerchantProfile merchant = new MerchantProfile();
        merchant.setUser(user);
        merchant.setBusinessName("Test Biz");
        merchant.setProfileCompleted(true);
        merchantProfileRepository.save(merchant);

        Product product = new Product();
        product.setMerchant(merchant);
        product.setName("Test Product");
        product.setPrice(new BigDecimal("100"));
        product.setGstRate(18);
        product.setActive(true);

        System.out.println("BEFORE SAVE: gstRate = " + product.getGstRate());
        productRepository.save(product);
        System.out.println("AFTER SAVE: product ID = " + product.getId());
        
        // Clear persistence context to force reload
        // (If using @SpringBootTest we might need to manually trigger flush or use a different test style)
    }
}
