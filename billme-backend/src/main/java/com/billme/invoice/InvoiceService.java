package com.billme.invoice;

import com.billme.customer.CustomerProfile;
import com.billme.email.InvoiceEmailService;
import com.billme.invoice.dto.CreateInvoiceItemRequest;
import com.billme.invoice.dto.CustomerInvoiceResponse;
import com.billme.invoice.dto.InvoiceItemResponse;
import com.billme.invoice.dto.PublicInvoiceResponse;
import com.billme.merchant.MerchantProfile;
import com.billme.payment.RazorpayService;
import com.billme.product.Product;
import com.billme.repository.*;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.billme.wallet.Wallet;
import com.billme.wallet.WalletService;
import com.billme.payment.PaymentSettlementService;
import org.springframework.web.server.ResponseStatusException;
import com.billme.notification.NotificationService; // Explicitly import NotificationService
import com.billme.notification.NotificationType;
import lombok.extern.slf4j.Slf4j; // Add this import for logging
import org.springframework.transaction.annotation.Isolation; // For strict transaction isolation

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j // Add this annotation for logging
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;
    private final RazorpayService razorpayService;
    private final InvoiceEmailService invoiceEmailService;
    private final WalletService walletService;
    private final InvoicePdfService invoicePdfService;
    private final NotificationService notificationService;
    private final TransactionRepository transactionRepository;
    private final PaymentSettlementService paymentSettlementService;
    @Value("${processing.fee.percent}")
    private BigDecimal processingFeePercent;

    private String generatePaymentToken() {
        return UUID.randomUUID()
                .toString()
                .replace("-", "")
                .substring(0, 10);
    }
    // =====================================================
    // CREATE INVOICE (STRICT PHASE 6 VERSION)
    // =====================================================
    @Transactional
    public void createInvoice(CreateInvoiceRequest request) {
        long start = System.currentTimeMillis();
        User user = getLoggedInUser();
        MerchantProfile merchant = merchantProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));

        validateMerchantProfile(merchant);

        Invoice invoice = new Invoice();
        invoice.setMerchant(merchant);
        
        // 🔥 OPTIMIZATION: BATCH FETCH PRODUCTS
        Map<Long, Product> productMap = batchFetchProducts(request, merchant);
        
        calculateAndPopulate(invoice, request, productMap);
        
        invoice.setPaymentToken(generatePaymentToken());
        invoice.setDueDate(LocalDate.now().plusDays(7)); // default 7 days
        
        Invoice savedInvoice = invoiceRepository.save(invoice);
        
        log.info("⏱️ [PERF] Invoice creation DB phase took {}ms", (System.currentTimeMillis() - start));

        // 🔔 In-App Notification (BEST EFFORT)
        try {
            if (savedInvoice.getCustomer() != null && savedInvoice.getCustomer().getUser() != null) {
                String msg = String.format("A new invoice #%s for ₹%s has been created for you by %s.",
                        savedInvoice.getInvoiceNumber(), savedInvoice.getTotalPayable(), savedInvoice.getMerchant().getBusinessName());
                notificationService.createNotification(savedInvoice.getCustomer().getUser(), msg, NotificationType.INVOICE_CREATED);
            }
        } catch (Exception e) {
            log.warn("⚠️ Notification failed: {}", e.getMessage());
        }

        // 📧 ASYNC EMAIL (NON-BLOCKING)
        invoiceEmailService.sendInvoiceEmail(savedInvoice);
    }

    private Map<Long, Product> batchFetchProducts(CreateInvoiceRequest request, MerchantProfile merchant) {
        Set<Long> productIds = request.getItems().stream()
                .map(CreateInvoiceItemRequest::getProductId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<String> barcodes = request.getItems().stream()
                .map(CreateInvoiceItemRequest::getBarcode)
                .filter(b -> b != null && !b.isBlank())
                .toList();

        Map<Long, Product> productMap = new HashMap<>();
        
        if (!productIds.isEmpty()) {
            productRepository.findAllById(productIds).forEach(p -> productMap.put(p.getId(), p));
        }
        
        if (!barcodes.isEmpty()) {
            productRepository.findByMerchantAndBarcodeIn(merchant, barcodes)
                    .forEach(p -> productMap.put(p.getId(), p));
        }
        
        return productMap;
    }

    @Transactional
    public void updateInvoice(Long invoiceId, CreateInvoiceRequest request) {
        User user = getLoggedInUser();
        Invoice invoice = invoiceRepository.findByIdAndMerchant_User_Id(invoiceId, user.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found or unauthorized"));

        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw new RuntimeException("Only UNPAID invoices can be edited");
        }

        // Invalidate old payment attempts
        invoice.setRazorpayOrderId(null);
        invoice.setPaymentInProgress(false);
        invoice.setPaymentStartedAt(null);

        // 🔥 OPTIMIZATION: BATCH FETCH PRODUCTS
        MerchantProfile merchant = invoice.getMerchant();
        Map<Long, Product> productMap = batchFetchProducts(request, merchant);

        // Clear items and recalculate
        invoice.getItems().clear();
        calculateAndPopulate(invoice, request, productMap);

        // 🔥 Generate NEW payment token to invalidate old links
        invoice.setPaymentToken(generatePaymentToken());

        invoiceRepository.save(invoice);
        
        // 🔔 In-App Notification (BEST EFFORT)
        try {
            if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null) {
                String msg = String.format("A new invoice #%s for ₹%s has been created for you by %s.",
                        invoice.getInvoiceNumber(), invoice.getTotalPayable(), invoice.getMerchant().getBusinessName());
                notificationService.createNotification(invoice.getCustomer().getUser(), msg, NotificationType.INVOICE_CREATED);
            }
        } catch (Exception e) {
            log.warn("⚠️ Notification failed: {}", e.getMessage());
        }

        // 📧 ASYNC EMAIL (NON-BLOCKING)
        invoiceEmailService.sendInvoiceEmail(invoice);
    }

    private void validateMerchantProfile(MerchantProfile merchant) {

        if (!merchant.isProfileCompleted()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Complete your profile before creating invoice"
            );
        }
    }

    private void calculateAndPopulate(Invoice invoice, CreateInvoiceRequest request, Map<Long, Product> productMap) {

        MerchantProfile merchant = invoice.getMerchant();

        if (request.getCustomerEmail() == null || request.getCustomerEmail().isBlank()) {
            throw new RuntimeException("Customer email is required");
        }

        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new RuntimeException("Invoice must contain at least one item");
        }

        invoice.setCustomerEmail(request.getCustomerEmail());
        invoice.setCustomerName(
                request.getCustomerName() != null ? request.getCustomerName() : "Customer"
        );

        // Link customer if exists - Reusing user check to avoid duplicate hits
        if (request.getCustomerEmail() != null) {
            userRepository.findByEmail(request.getCustomerEmail()).ifPresent(u -> {
                customerProfileRepository.findByUser_Id(u.getId()).ifPresent(invoice::setCustomer);
            });
        }

        // ================= PLACE OF SUPPLY =================
        String placeOfSupply = request.getCustomerState();

        if ((placeOfSupply == null || placeOfSupply.isBlank()) && invoice.getCustomer() != null) {
            placeOfSupply = invoice.getCustomer().getState();
        }

        if (placeOfSupply == null || placeOfSupply.isBlank()) {
            placeOfSupply = merchant.getState();
        }

        boolean isIntraState =
                merchant.getState() != null &&
                        merchant.getState().equalsIgnoreCase(placeOfSupply);

        // ================= TOTALS =================
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal cgstTotal = BigDecimal.ZERO;
        BigDecimal sgstTotal = BigDecimal.ZERO;
        BigDecimal igstTotal = BigDecimal.ZERO;

        // ================= ITEMS LOOP =================
        for (CreateInvoiceItemRequest itemRequest : request.getItems()) {

            if (itemRequest.getQuantity() == null || itemRequest.getQuantity() <= 0) {
                throw new RuntimeException("Quantity must be greater than zero");
            }

            Product product = resolveProductFromMap(itemRequest, merchant, productMap);

            if (!product.isActive()) {
                throw new RuntimeException("Product is not active: " + product.getName());
            }

            BigDecimal quantity = BigDecimal.valueOf(itemRequest.getQuantity());
            BigDecimal unitPrice = product.getPrice();
            BigDecimal baseAmount = unitPrice.multiply(quantity);

            // ================= GST =================
            BigDecimal gstRate = merchant.isGstRegistered()
                    ? BigDecimal.valueOf(product.getGstRate())
                    : BigDecimal.ZERO;

            BigDecimal itemGst = baseAmount
                    .multiply(gstRate)
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

            BigDecimal itemCgst = BigDecimal.ZERO;
            BigDecimal itemSgst = BigDecimal.ZERO;
            BigDecimal itemIgst = BigDecimal.ZERO;

            if (isIntraState) {
                itemCgst = itemGst.divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
                itemSgst = itemGst.subtract(itemCgst);
            } else {
                itemIgst = itemGst;
            }

            BigDecimal lineTotal = baseAmount.add(itemGst);

            // ================= 🚨 FIX HERE =================
            InvoiceItem item = InvoiceItem.builder()
                    .invoice(invoice)
                    .product(product)
                    .productNameSnapshot(product.getName())
                    .unitPrice(unitPrice)
                    .quantity(itemRequest.getQuantity())
                    .baseAmount(baseAmount)
                    .gstRate(gstRate)
                    .gstAmount(itemGst)
                    .gstTotal(itemGst) // ✅ 🔥 FIX: THIS WAS MISSING
                    .cgstAmount(itemCgst)
                    .sgstAmount(itemSgst)
                    .igstAmount(itemIgst)
                    .totalPrice(lineTotal)
                    .build();

            invoice.getItems().add(item);

            // ================= ACCUMULATE =================
            subtotal = subtotal.add(baseAmount);
            cgstTotal = cgstTotal.add(itemCgst);
            sgstTotal = sgstTotal.add(itemSgst);
            igstTotal = igstTotal.add(itemIgst);
        }

        // ================= FINAL TOTALS =================
        BigDecimal totalGst = cgstTotal.add(sgstTotal).add(igstTotal);

        BigDecimal processingFee = subtotal
                .multiply(processingFeePercent)
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);

        BigDecimal totalPayable = subtotal
                .add(totalGst)
                .add(processingFee)
                .setScale(2, RoundingMode.HALF_UP);

        // ================= SET INVOICE =================
        invoice.setSubtotal(subtotal);
        invoice.setCgstTotal(cgstTotal);
        invoice.setSgstTotal(sgstTotal);
        invoice.setIgstTotal(igstTotal);
        invoice.setGstTotal(totalGst);
        invoice.setProcessingFee(processingFee);
        invoice.setTotalPayable(totalPayable);
        invoice.setAmount(totalPayable);
    }

    // =====================================================
    // PRODUCT RESOLUTION (ID OR BARCODE)
    // =====================================================
    private Product resolveProductFromMap(CreateInvoiceItemRequest itemRequest,
                                          MerchantProfile merchant,
                                          Map<Long, Product> productMap) {

        Product product = null;
        if (itemRequest.getProductId() != null) {
            product = productMap.get(itemRequest.getProductId());
        } else if (itemRequest.getBarcode() != null) {
            product = productMap.values().stream()
                    .filter(p -> itemRequest.getBarcode().equals(p.getBarcode()))
                    .findFirst()
                    .orElse(null);
        }

        if (product == null) {
            throw new RuntimeException("Product not found: " + 
                (itemRequest.getProductId() != null ? itemRequest.getProductId() : itemRequest.getBarcode()));
        }

        if (!product.getMerchant().getId().equals(merchant.getId())) {
            throw new RuntimeException("Unauthorized product access");
        }

        return product;
    }

    // =====================================================
    // CUSTOMER RETRIEVAL APIs
    // =====================================================
    @Transactional(readOnly = true)
    public List<CustomerInvoiceResponse> getCustomerInvoices(String email) {

        List<Invoice> invoices =
                invoiceRepository.findByCustomerUserEmail(email);

        return invoices.stream()
                .map(this::mapToResponse)
                .toList();
    }

    public byte[] generateInvoicePdf(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return invoicePdfService.generateInvoicePdf(invoice);
    }
    @Transactional(readOnly = true)
    public List<CustomerInvoiceResponse> getPendingInvoices(String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return invoiceRepository
                .findByCustomer_User_IdAndStatus(user.getId(), InvoiceStatus.UNPAID)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CustomerInvoiceResponse getInvoiceById(Long id, String email) {

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(id, user.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        return mapToResponse(invoice);
    }

    // =====================================================
    // MERCHANT INVOICE RETRIEVAL
    // =====================================================
    @Transactional(readOnly = true)
    public List<CustomerInvoiceResponse> getMerchantInvoices(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return invoiceRepository.findByMerchant_User_Id(user.getId())
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    // =====================================================
    // STATE MACHINE ENFORCEMENT
    // =====================================================
    private void validateStatusTransition(Invoice invoice, InvoiceStatus newStatus) {

        InvoiceStatus current = invoice.getStatus();

        if (current == InvoiceStatus.PAID) {
            throw new RuntimeException("Paid invoice cannot be modified");
        }

        if (current == InvoiceStatus.UNPAID &&
                (newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.FAILED || newStatus == InvoiceStatus.PENDING)) {
            return;
        }

        if (current == InvoiceStatus.PENDING &&
                (newStatus == InvoiceStatus.PAID || newStatus == InvoiceStatus.FAILED)) {
            return;
        }

        throw new RuntimeException("Invalid invoice status transition");
    }

    @Transactional
    public void markInvoicePaid(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        validateStatusTransition(invoice, InvoiceStatus.PAID);

        invoice.setStatus(InvoiceStatus.PAID);
        invoice.setPaidAt(LocalDateTime.now());
    }

    @Transactional
    public void markInvoiceFailed(Long invoiceId) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        validateStatusTransition(invoice, InvoiceStatus.FAILED);

        invoice.setStatus(InvoiceStatus.FAILED);
    }

    // =====================================================
    // MAPPING
    // =====================================================
    private CustomerInvoiceResponse mapToResponse(Invoice invoice) {

        BigDecimal cgst = invoice.getCgstTotal() != null ? invoice.getCgstTotal() : BigDecimal.ZERO;
        BigDecimal sgst = invoice.getSgstTotal() != null ? invoice.getSgstTotal() : BigDecimal.ZERO;
        BigDecimal igst = invoice.getIgstTotal() != null ? invoice.getIgstTotal() : BigDecimal.ZERO;

        // Backward compatibility for legacy invoices
        if (invoice.getCgstTotal() == null && invoice.getIgstTotal() == null && invoice.getGstTotal() != null) {
            cgst = invoice.getGstTotal().divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
            sgst = invoice.getGstTotal().subtract(cgst);
        }

        return CustomerInvoiceResponse.builder()
                .invoiceId(invoice.getId())
                .invoiceNumber(invoice.getInvoiceNumber())
                .merchantName(invoice.getMerchant() != null ? invoice.getMerchant().getBusinessName() : "Merchant")
                .amount(invoice.getTotalPayable())
                .subtotal(invoice.getSubtotal())
                .processingFee(invoice.getProcessingFee())
                .totalPayable(invoice.getTotalPayable())
                .cgstAmount(cgst)
                .sgstAmount(sgst)
                .igstAmount(igst)
                .status(invoice.getStatus().name())
                .paymentToken(invoice.getPaymentToken())
                .customerName(invoice.getCustomerName())
                .customerEmail(invoice.getResolvedCustomerEmail())
                .paymentMethod(invoice.getPaymentMethod() != null
                        ? invoice.getPaymentMethod().name()
                        : null)
                .issuedAt(invoice.getIssuedAt())
                .dueDate(
                        invoice.getDueDate() != null
                                ? invoice.getDueDate().atStartOfDay()
                                : null
                )
                .paidAt(invoice.getPaidAt())
                .refundWindowExpiry(invoice.getRefundWindowExpiry())
                .items(
                        invoice.getItems().stream()
                                .map(item ->
                                        InvoiceItemResponse.builder()
                                                .productId(item.getProduct().getId())
                                                .productName(item.getProductNameSnapshot())
                                                .unitPrice(item.getUnitPrice())
                                                .quantity(item.getQuantity())
                                                .gstRate(item.getGstRate())
                                                .gstAmount(item.getGstAmount())
                                                .cgstAmount(item.getCgstAmount())
                                                .sgstAmount(item.getSgstAmount())
                                                .igstAmount(item.getIgstAmount())
                                                .totalPrice(item.getTotalPrice())
                                                .build()
                                ).toList()
                )
                .build();
    }
    // =====================================================
    private User getLoggedInUser() {
        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
    }
    @Transactional
    public void payInvoiceWithFacePay(Long invoiceId) {

        User user = getLoggedInUser();

        // 🔐 Fetch invoice securely (customer ownership)
        Invoice invoice = invoiceRepository
                .findByIdAndCustomer_User_Id(invoiceId, user.getId())
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        // 🔒 Must be UNPAID
        if (invoice.getStatus() != InvoiceStatus.UNPAID) {
            throw new RuntimeException("Invoice is not payable");
        }

        // Set FacePay method for settlement
        invoice.setPaymentMethod(PaymentMethod.FACE_PAY);
        invoiceRepository.saveAndFlush(invoice);

        // 🔥 Centralized Settlement Logic
        paymentSettlementService.settlePayment(invoiceId, "FACEPAY-" + invoice.getInvoiceNumber());
    }
    @Transactional
    public String createRazorpayOrder(Long invoiceId, String token) {

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        // Validate Token or Ownership
        boolean isAuthorized = false;
        if (token != null && !token.isBlank()) {
            if (invoice.getPaymentToken().equals(token)) {
                isAuthorized = true;
                // Token Expiry Check
                if (invoice.getDueDate() != null && LocalDateTime.now().isAfter(invoice.getDueDate().atTime(23, 59))) {
                    log.warn("❌ [TOKEN EXPIRED] Payment token expired for Invoice {}", invoice.getInvoiceNumber());
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment link has expired");
                }
            }
        }
        
        if (!isAuthorized) {
            try {
                User user = getLoggedInUser();
                if (invoice.getCustomer() != null && invoice.getCustomer().getUser() != null 
                    && invoice.getCustomer().getUser().getId().equals(user.getId())) {
                    isAuthorized = true;
                }
            } catch (Exception e) {
                // Not authenticated
            }
        }

        if (!isAuthorized) {
            log.warn("🚨 [UNAUTHORIZED] Invalid token access attempt for Invoice {}", invoice.getInvoiceNumber());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment token or unauthorized");
        }

        // ❌ BLOCK if already paid
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.warn("❌ [RETRY BLOCKED] Invoice {} already PAID", invoice.getInvoiceNumber());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Payment already completed for this invoice");
        }

        // ❌ BLOCK if not in PENDING or UNPAID or FAILED
        if (invoice.getStatus() != InvoiceStatus.UNPAID &&
                invoice.getStatus() != InvoiceStatus.PENDING &&
                invoice.getStatus() != InvoiceStatus.FAILED) {
            log.warn("❌ [RETRY BLOCKED] Invoice {} status {} not eligible", invoice.getInvoiceNumber(), invoice.getStatus());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice status is not eligible for payment");
        }

        // 🔥 Payment lock check - Sensed that we already have an active session?
        if (Boolean.TRUE.equals(invoice.getPaymentInProgress())) {

            // 1. If we already have a Razorpay Order ID, just return it (Reuse)
            if (invoice.getRazorpayOrderId() != null) {
                log.info("♻️ [ORDER REUSE] Returning existing Razorpay order: {} for Invoice: {}",
                        invoice.getRazorpayOrderId(), invoice.getInvoiceNumber());
                return invoice.getRazorpayOrderId();
            }

            // 2. If no order ID but lock is old, release it
            if (invoice.getPaymentStartedAt() != null &&
                    invoice.getPaymentStartedAt().isBefore(LocalDateTime.now().minusMinutes(10))) {

                log.info("🕒 [LOCK RELEASED] Releasing stagnant lock for Invoice: {}", invoice.getInvoiceNumber());
                invoice.setPaymentInProgress(false);
                invoice.setPaymentStartedAt(null);
                invoice.setRazorpayOrderId(null);
            } else {
                // 3. Otherwise, block (this is the true race condition guard)
                log.warn("⏳ [PAYMENT BLOCKED] Payment already in progress for Invoice: {}", invoice.getInvoiceNumber());
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Payment already in progress. Please wait.");
            }
        }

        // 🚀 Create New Order
        log.info("💳 [ORDER START] Creating Razorpay order for Invoice: {} | Status: {}",
                invoice.getInvoiceNumber(), invoice.getStatus());

        var order = razorpayService.createOrder(invoice);

        invoice.setRazorpayOrderId(order.get("id"));
        invoice.setStatus(InvoiceStatus.PENDING);
        invoice.setPaymentInProgress(true);
        invoice.setPaymentStartedAt(LocalDateTime.now());

        log.info("✅ [ORDER CREATED] ID: {} for Invoice: {}", invoice.getRazorpayOrderId(), invoice.getInvoiceNumber());

        return invoice.getRazorpayOrderId();
    }

    @Transactional(isolation = Isolation.SERIALIZABLE)
    public String retryPayment(Long invoiceId) {
        log.info("🔁 [RETRY TRIGGERED] Manual retry for Invoice ID: {}", invoiceId);

        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Invoice not found"));

        // 1. Validate Status - BLOCK if PAID
        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.warn("❌ [RETRY BLOCKED] Invoice {} is already PAID", invoice.getInvoiceNumber());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invoice is already paid");
        }

        // 2. CRITICAL: Force Reset of Payment Lock
        log.info("🧹 [CLEANUP] Resetting paymentInProgress and clearing old Razorpay data for Invoice: {}", invoice.getInvoiceNumber());
        invoice.setPaymentInProgress(false);
        invoice.setRazorpayOrderId(null);
        invoice.setPaymentStartedAt(null);

        // 🔥 Save flush to ensure DB state is clean BEFORE next step
        invoiceRepository.saveAndFlush(invoice);
        log.info("✅ [RESET SUCCESS] paymentInProgress set to FALSE for Invoice: {}", invoice.getInvoiceNumber());

        // 3. Create fresh order
        return createRazorpayOrder(invoiceId, null);
    }

    @Transactional(readOnly = true)
    public PublicInvoiceResponse getPublicInvoice(String invoiceNumber, String token) {

        Invoice invoice = invoiceRepository.findByInvoiceNumber(invoiceNumber)
                .orElseThrow(() -> new RuntimeException("Invoice not found"));

        if (!invoice.getPaymentToken().equals(token)) {
            log.warn("🚨 [UNAUTHORIZED] Invalid token access attempt for public invoice {}", invoiceNumber);
            throw new RuntimeException("Invalid payment token");
        }

        // Only enforce expiry logic for public fetching if it's UNPAID/PENDING.
        // Once PAID, we allow customers to view the receipt even after due date.
        if (invoice.getStatus() != InvoiceStatus.PAID && invoice.getDueDate() != null 
            && LocalDateTime.now().isAfter(invoice.getDueDate().atTime(23, 59))) {
            log.warn("❌ [TOKEN EXPIRED] Public invoice link expired for Invoice {}", invoiceNumber);
            throw new RuntimeException("Payment link has expired");
        }

        MerchantProfile merchant = invoice.getMerchant();

        BigDecimal gstTotal = invoice.getItems().stream()
                .map(InvoiceItem::getGstAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<InvoiceItemResponse> items = invoice.getItems().stream()
                .map(item -> InvoiceItemResponse.builder()
                        .productId(item.getProduct().getId())
                        .productName(item.getProductNameSnapshot())
                        .unitPrice(item.getUnitPrice())
                        .quantity(item.getQuantity())
                        .gstRate(item.getGstRate())
                        .gstAmount(item.getGstAmount())
                        .totalPrice(item.getTotalPrice())
                        .build())
                .toList();

        return new PublicInvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                merchant.getBusinessName(),
                merchant.getGstin(),
                invoice.getResolvedCustomerEmail(),
                items,
                invoice.getSubtotal(),
                invoice.getCgstTotal() != null ? invoice.getCgstTotal() : BigDecimal.ZERO,
                invoice.getSgstTotal() != null ? invoice.getSgstTotal() : BigDecimal.ZERO,
                invoice.getIgstTotal() != null ? invoice.getIgstTotal() : BigDecimal.ZERO,
                invoice.getGstTotal() != null ? invoice.getGstTotal() : BigDecimal.ZERO,
                invoice.getProcessingFee(),
                invoice.getTotalPayable(),
                invoice.getStatus().name()
        );
    }

    @Transactional
    public void verifyRazorpayPayment(com.billme.payment.dto.VerifyRazorpayRequest request) {

        Invoice invoice = invoiceRepository.findByRazorpayOrderId(request.getRazorpay_order_id())
                .orElseThrow(() -> new RuntimeException("Invoice not found for order ID: " + request.getRazorpay_order_id()));

        if (invoice.getStatus() == InvoiceStatus.PAID) {
            log.info("⚠️ [IDEMPOTENCY] Invoice {} already paid. Skipping settlement.", invoice.getInvoiceNumber());
            return;
        }

        // Set UPI method for settlement
        invoice.setPaymentMethod(PaymentMethod.UPI_PAY);
        invoice.setPaymentInProgress(false);
        invoiceRepository.saveAndFlush(invoice);

        // 🔥 Centralized Settlement Logic
        paymentSettlementService.settlePayment(invoice.getId(), request.getRazorpay_payment_id());

        log.info("✅ [PAYMENT VERIFIED & SETTLED] Invoice: {} verified via Razorpay.", invoice.getInvoiceNumber());
    }
}