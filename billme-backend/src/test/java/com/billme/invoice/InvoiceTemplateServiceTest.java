package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.merchant.MerchantProfile;
import com.billme.product.Product;
import com.billme.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class InvoiceTemplateServiceTest {

    @Test
    public void testGenerateInvoiceHtml_GstGrouping() {
        ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
        templateResolver.setPrefix("templates/");
        templateResolver.setSuffix(".html");
        templateResolver.setTemplateMode(TemplateMode.HTML);

        TemplateEngine engine = new TemplateEngine();
        engine.setTemplateResolver(templateResolver);

        InvoiceTemplateService service = new InvoiceTemplateService(engine);

        // Build mock data
        User merchantUser = new User();
        merchantUser.setEmail("merchant@test.com");
        MerchantProfile merchant = new MerchantProfile();
        merchant.setBusinessName("Test Merchant");
        merchant.setAddress("123 Merchant St");
        merchant.setGstin("22AAAAA0000A1Z5");
        merchant.setGstRegistered(true);
        merchant.setUser(merchantUser);

        User customerUser = new User();
        customerUser.setEmail("customer@test.com");
        CustomerProfile customer = new CustomerProfile();
        customer.setName("Test Customer");
        customer.setAddress("456 Customer Ave");
        customer.setUser(customerUser);

        Product p1 = new Product();
        p1.setName("Item A");
        p1.setPrice(new BigDecimal("100.00"));
        p1.setGstRate(12);

        Product p2 = new Product();
        p2.setName("Item B");
        p2.setPrice(new BigDecimal("200.00"));
        p2.setGstRate(18);

        Invoice invoice = new Invoice();
        ReflectionTestUtils.setField(invoice, "id", 1000L);
        invoice.setInvoiceNumber("INV-001");
        invoice.setIssuedAt(LocalDateTime.now());
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setMerchant(merchant);
        invoice.setCustomer(customer);
        invoice.setSubtotal(new BigDecimal("300.00"));
        invoice.setProcessingFee(new BigDecimal("0.00"));
        invoice.setTotalPayable(new BigDecimal("348.00"));

        InvoiceItem item1 = new InvoiceItem();
        item1.setInvoice(invoice);
        item1.setProduct(p1);
        item1.setProductNameSnapshot(p1.getName());
        item1.setUnitPrice(p1.getPrice());
        item1.setQuantity(1);
        item1.setGstRate(BigDecimal.valueOf(p1.getGstRate()));
        item1.setGstAmount(new BigDecimal("12.00"));
        item1.setTotalPrice(new BigDecimal("112.00"));

        InvoiceItem item2 = new InvoiceItem();
        item2.setInvoice(invoice);
        item2.setProduct(p2);
        item2.setProductNameSnapshot(p2.getName());
        item2.setUnitPrice(p2.getPrice());
        item2.setQuantity(1);
        item2.setGstRate(BigDecimal.valueOf(p2.getGstRate()));
        item2.setGstAmount(new BigDecimal("36.00"));
        item2.setTotalPrice(new BigDecimal("236.00"));

        invoice.setItems(List.of(item1, item2));

        String html = service.generateInvoiceHtml(invoice);
        
        System.out.println("--- HTML OUTPUT START ---");
        System.out.println(html);
        System.out.println("--- HTML OUTPUT END ---");
    }
}
