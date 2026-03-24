package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.email.InvoiceEmailService;
import com.billme.invoice.dto.CreateInvoiceItemRequest;
import com.billme.invoice.dto.CustomerInvoiceResponse;
import com.billme.merchant.MerchantProfile;
import com.billme.payment.RazorpayService;
import com.billme.product.Product;
import com.billme.repository.*;
import com.billme.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private MerchantProfileRepository merchantProfileRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private ProductRepository productRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private RazorpayService razorpayService;
    @Mock
    private InvoiceEmailService invoiceEmailService;
    @Mock
    private SecurityContext securityContext;
    @Mock
    private Authentication authentication;

    @InjectMocks
    private InvoiceService invoiceService;

    private User testUser;
    private MerchantProfile testMerchant;
    private CustomerProfile testCustomer;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(invoiceService, "processingFeePercent", BigDecimal.valueOf(2));

        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("merchant@billme.com");

        testMerchant = new MerchantProfile();
        testMerchant.setId(10L);
        testMerchant.setUser(testUser);
        testMerchant.setProfileCompleted(true);
        testMerchant.setGstRegistered(true);

        User customerUser = new User();
        customerUser.setId(2L);
        customerUser.setEmail("customer@billme.com");

        testCustomer = new CustomerProfile();
        testCustomer.setId(20L);
        testCustomer.setUser(customerUser);
    }

    private void mockSecurityContext() {
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getName()).thenReturn("merchant@billme.com");
        lenient().when(userRepository.findByEmail("merchant@billme.com")).thenReturn(Optional.of(testUser));
    }

    @Test
    void testCreateInvoice_MultiItemMixedGst() {
        mockSecurityContext();

        when(merchantProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(testMerchant));
        when(customerProfileRepository.findById(20L)).thenReturn(Optional.of(testCustomer));

        Product product1 = new Product();
        ReflectionTestUtils.setField(product1, "id", 100L);
        product1.setMerchant(testMerchant);
        product1.setPrice(new BigDecimal("100.00")); // quantity 2 = 200
        product1.setGstRate(12); // 24 GST
        product1.setActive(true);

        Product product2 = new Product();
        ReflectionTestUtils.setField(product2, "id", 101L);
        product2.setMerchant(testMerchant);
        product2.setPrice(new BigDecimal("200.00")); // quantity 1 = 200
        product2.setGstRate(18); // 36 GST
        product2.setActive(true);

        when(productRepository.findById(100L)).thenReturn(Optional.of(product1));
        when(productRepository.findById(101L)).thenReturn(Optional.of(product2));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setCustomerEmail("customer@billme.com");
        
        CreateInvoiceItemRequest req1 = new CreateInvoiceItemRequest();
        req1.setProductId(100L);
        req1.setQuantity(2);
        
        CreateInvoiceItemRequest req2 = new CreateInvoiceItemRequest();
        req2.setProductId(101L);
        req2.setQuantity(1);
        
        request.setItems(List.of(req1, req2));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice captured = i.getArgument(0);
            captured.setId(99L);
            return captured;
        });

        invoiceService.createInvoice(request);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());

        Invoice savedInvoice = invoiceCaptor.getValue();
        
        assertEquals(2, savedInvoice.getItems().size());
        
        // Product 1 Assertions
        InvoiceItem item1 = savedInvoice.getItems().get(0);
        assertEquals(new BigDecimal("12.00"), item1.getGstRate());
        assertEquals(new BigDecimal("24.00"), item1.getGstAmount());
        
        // Product 2 Assertions
        InvoiceItem item2 = savedInvoice.getItems().get(1);
        assertEquals(new BigDecimal("18.00"), item2.getGstRate());
        assertEquals(new BigDecimal("36.00"), item2.getGstAmount());

        // Invoice Totals
        assertEquals(new BigDecimal("400.00"), savedInvoice.getSubtotal());
        assertEquals(new BigDecimal("460.00"), savedInvoice.getTotalPayable()); // 400 + 60 GST (Customer Pays)
        assertEquals(new BigDecimal("9.20"), savedInvoice.getProcessingFee());  // 2% of 460
        assertEquals(new BigDecimal("460.00"), savedInvoice.getAmount());       // Total amount
    }

    @Test
    void testCreateInvoice_SettlementLogic() {
        mockSecurityContext();

        when(merchantProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(testMerchant));
        when(customerProfileRepository.findById(20L)).thenReturn(Optional.of(testCustomer));

        Product product = new Product();
        ReflectionTestUtils.setField(product, "id", 100L);
        product.setMerchant(testMerchant);
        product.setPrice(new BigDecimal("300.00")); 
        product.setGstRate(16); // 16% of 300 = 48.00 
        product.setActive(true);

        when(productRepository.findById(100L)).thenReturn(Optional.of(product));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setCustomerEmail("customer@billme.com");
        CreateInvoiceItemRequest req1 = new CreateInvoiceItemRequest();
        req1.setProductId(100L);
        req1.setQuantity(1);
        request.setItems(List.of(req1));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
             Invoice captured = i.getArgument(0);
             captured.setId(99L);
             return captured;
        });

        invoiceService.createInvoice(request);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());

        Invoice savedInvoice = invoiceCaptor.getValue();
        
        // Scenario 1 user verification:
        // subtotal = 300
        // gstTotal = 48
        // totalPayable = 348
        // processingFeePercent = 2
        // processingFee = 6.96
        
        assertEquals(new BigDecimal("300.00"), savedInvoice.getSubtotal());
        assertEquals(new BigDecimal("348.00"), savedInvoice.getTotalPayable());
        assertEquals(new BigDecimal("6.96"), savedInvoice.getProcessingFee());
        assertEquals(new BigDecimal("348.00"), savedInvoice.getAmount());
    }

    @Test
    void testCreateInvoice_NonGstMerchant() {
        mockSecurityContext();

        testMerchant.setGstRegistered(false);

        when(merchantProfileRepository.findByUser_Id(1L)).thenReturn(Optional.of(testMerchant));
        when(customerProfileRepository.findById(20L)).thenReturn(Optional.of(testCustomer));

        Product product1 = new Product();
        ReflectionTestUtils.setField(product1, "id", 100L);
        product1.setMerchant(testMerchant);
        product1.setPrice(new BigDecimal("100.00")); 
        product1.setGstRate(18); // Should be overridden to 0
        product1.setActive(true);

        when(productRepository.findById(100L)).thenReturn(Optional.of(product1));

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setCustomerEmail("customer@billme.com");
        CreateInvoiceItemRequest req1 = new CreateInvoiceItemRequest();
        req1.setProductId(100L);
        req1.setQuantity(1);
        request.setItems(List.of(req1));

        when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> {
            Invoice captured = i.getArgument(0);
            captured.setId(99L);
            return captured;
        });

        invoiceService.createInvoice(request);

        ArgumentCaptor<Invoice> invoiceCaptor = ArgumentCaptor.forClass(Invoice.class);
        verify(invoiceRepository).save(invoiceCaptor.capture());

        Invoice savedInvoice = invoiceCaptor.getValue();
        InvoiceItem item1 = savedInvoice.getItems().get(0);
        
        assertEquals(new BigDecimal("0.00"), item1.getGstRate());
        assertEquals(new BigDecimal("0.00"), item1.getGstAmount());
    }

    @Test
    void testGetInvoiceById_CgstSgstSplit() {
        User myUser = new User();
        myUser.setId(2L);
        myUser.setEmail("customer@billme.com");

        when(userRepository.findByEmail("customer@billme.com")).thenReturn(Optional.of(myUser));

        Invoice invoice = new Invoice();
        invoice.setId(500L);
        invoice.setCustomer(testCustomer);
        invoice.setMerchant(testMerchant);
        invoice.setSubtotal(new BigDecimal("100.00"));
        invoice.setProcessingFee(new BigDecimal("2.00"));
        invoice.setTotalPayable(new BigDecimal("120.00"));
        invoice.setStatus(InvoiceStatus.UNPAID);
        invoice.setInvoiceNumber("INV-123");

        InvoiceItem item1 = new InvoiceItem();
        item1.setGstAmount(new BigDecimal("18.00")); // Total GST

        invoice.setItems(List.of(item1));

        when(invoiceRepository.findByIdAndCustomer_User_Id(500L, 2L)).thenReturn(Optional.of(invoice));
        
        CustomerInvoiceResponse response = invoiceService.getInvoiceById(500L, "customer@billme.com");

        assertEquals(new BigDecimal("9.00"), response.getCgstAmount());
        assertEquals(new BigDecimal("9.00"), response.getSgstAmount());
    }

    @Test
    void testPayInvoiceWithFacePay_ReconciliationFailure() {
        mockSecurityContext();

        Invoice invoice = spy(new Invoice());
        invoice.setId(500L);
        invoice.setCustomer(testCustomer);
        invoice.setMerchant(testMerchant);
        invoice.setStatus(InvoiceStatus.UNPAID);
        
        // Here we trigger the mismatch using a native BigDecimal scale difference.
        // A="100.00" (scale 2), B="5.000" (scale 3). (A - B) + B gives scale 3.
        // A.equals(calculated) evaluates to false due to strictly different scales.
        invoice.setTotalPayable(new BigDecimal("100.00"));
        when(invoice.getProcessingFee()).thenReturn(new BigDecimal("5.000"));

        com.billme.wallet.Wallet merchantWallet = new com.billme.wallet.Wallet();
        merchantWallet.setBalance(new BigDecimal("0.00"));
        testMerchant.getUser().setWallet(merchantWallet);
        
        com.billme.wallet.Wallet customerWallet = new com.billme.wallet.Wallet();
        customerWallet.setBalance(new BigDecimal("200.00"));
        testCustomer.getUser().setWallet(customerWallet);

        when(invoiceRepository.findByIdAndCustomer_User_Id(500L, 1L)).thenReturn(Optional.of(invoice));

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            invoiceService.payInvoiceWithFacePay(500L);
        });

        assertEquals("Financial reconciliation failed", exception.getMessage());
    }
}
