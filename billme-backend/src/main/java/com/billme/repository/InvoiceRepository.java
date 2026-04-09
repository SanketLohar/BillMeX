package com.billme.repository;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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

    @EntityGraph(attributePaths = {"items"})
    List<Invoice> findByMerchant_IdAndStatusInAndPaidAtBetween(
            Long merchantId, 
            List<InvoiceStatus> statuses, 
            java.time.LocalDateTime startDate, 
            java.time.LocalDateTime endDate
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

    @Query(value = "SELECT DATE(CONVERT_TZ(paid_at, '+00:00', '+05:30')) as bucket, COALESCE(SUM(amount), 0) as total FROM invoices " +
                   "WHERE merchant_id = :merchantId AND status = 'PAID' " +
                   "AND paid_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findDailyRevenue(@Param("merchantId") Long merchantId,
                                  @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(CONVERT_TZ(paid_at, '+00:00', '+05:30'), '%x-W%v') as bucket, COALESCE(SUM(amount), 0) as total FROM invoices " +
                   "WHERE merchant_id = :merchantId AND status = 'PAID' " +
                   "AND paid_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findWeeklyRevenue(@Param("merchantId") Long merchantId, 
                                   @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(CONVERT_TZ(paid_at, '+00:00', '+05:30'), '%Y-%m') as bucket, COALESCE(SUM(amount), 0) as total FROM invoices " +
                   "WHERE merchant_id = :merchantId AND status = 'PAID' " +
                   "AND paid_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findMonthlyRevenue(@Param("merchantId") Long merchantId, 
                                    @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    @Query(value = "SELECT YEAR(CONVERT_TZ(paid_at, '+00:00', '+05:30')) as bucket, COALESCE(SUM(amount), 0) as total FROM invoices " +
                   "WHERE merchant_id = :merchantId AND status = 'PAID' " +
                   "AND paid_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findYearlyRevenue(@Param("merchantId") Long merchantId, 
                                   @Param("start") java.time.LocalDateTime start, @Param("end") java.time.LocalDateTime end);

    Optional<Invoice> findByInvoiceNumberAndPaymentToken(String invoiceNumber, String paymentToken);
}