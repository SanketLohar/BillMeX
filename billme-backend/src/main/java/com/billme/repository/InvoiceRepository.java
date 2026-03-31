package com.billme.repository;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

public interface InvoiceRepository extends JpaRepository<Invoice, Long> {
    @EntityGraph(attributePaths = {"items", "merchant", "customer", "customer.user", "merchant.user"})
    Optional<Invoice> findWithDetailsById(Long id);

    // Customer invoice fetch
    List<Invoice> findByCustomer_User_Id(Long userId);

    List<Invoice> findByCustomer_User_IdAndStatus(Long userId, InvoiceStatus status);

    Optional<Invoice> findByInvoiceNumber(String invoiceNumber);

    Optional<Invoice> findByIdAndCustomer_User_Id(Long invoiceId, Long userId);
    Optional<Invoice> findByRazorpayOrderId(String razorpayOrderId);
    // Merchant invoice fetch
    @EntityGraph(attributePaths = {"items", "customer"})
    List<Invoice> findByMerchant_User_Id(Long userId);

    @EntityGraph(attributePaths = {"items", "customer"})
    org.springframework.data.domain.Page<Invoice> findByMerchant_User_Id(
            Long userId, 
            org.springframework.data.domain.Pageable pageable
    );

    Optional<Invoice> findByIdAndMerchant_User_Id(Long invoiceId, Long userId);

    List<Invoice> findByMerchant_User_IdAndStatus(
            Long userId,
            InvoiceStatus status
    );
    @EntityGraph(attributePaths = {"items", "merchant"})
    List<Invoice> findByCustomerUserEmail(String email);
    @Query("SELECT COALESCE(SUM(i.amount), 0) FROM Invoice i WHERE i.merchant.id = :merchantId AND i.status = :status")
    BigDecimal sumAmountByMerchantIdAndStatus(@org.springframework.data.repository.query.Param("merchantId") Long merchantId, @org.springframework.data.repository.query.Param("status") InvoiceStatus status);

    @Query("SELECT COALESCE(SUM(i.processingFee), 0) FROM Invoice i WHERE i.merchant.id = :merchantId AND i.status = :status")
    BigDecimal sumProcessingFeeByMerchantIdAndStatus(@org.springframework.data.repository.query.Param("merchantId") Long merchantId, @org.springframework.data.repository.query.Param("status") InvoiceStatus status);

    @Query("""
       SELECT COALESCE(SUM(i.amount), 0)
       FROM Invoice i
       WHERE i.status = com.billme.invoice.InvoiceStatus.PAID
       AND i.refundWindowExpiry > CURRENT_TIMESTAMP
       """)
    BigDecimal sumLockedAmount();

    Optional<Invoice> findByInvoiceNumberAndPaymentToken(String invoiceNumber, String paymentToken);
}