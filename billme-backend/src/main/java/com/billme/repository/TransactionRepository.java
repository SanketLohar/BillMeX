package com.billme.repository;

import com.billme.payment.dto.MerchantRefundResponse;
import com.billme.transaction.Transaction;
import com.billme.transaction.TransactionStatus;
import com.billme.transaction.TransactionType;
import com.billme.wallet.Wallet;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    @Query("""
SELECT DISTINCT t FROM Transaction t
LEFT JOIN FETCH t.senderWallet sw
LEFT JOIN FETCH sw.user su
LEFT JOIN FETCH t.receiverWallet rw
LEFT JOIN FETCH rw.user ru
LEFT JOIN FETCH t.invoice i
WHERE (
    (sw IS NOT NULL AND su.id = :userId)
    OR
    (rw IS NOT NULL AND ru.id = :userId)
)
AND (:type IS NULL OR t.transactionType = :type)
AND (:status IS NULL OR t.status = :status)
AND (:fromDate IS NULL OR t.createdAt >= :fromDate)
AND (:toDate IS NULL OR t.createdAt <= :toDate)
ORDER BY t.createdAt DESC
""")
    Page<Transaction> findUserTransactions(
            @Param("userId") Long userId,
            @Param("type") TransactionType type,
            @Param("status") TransactionStatus status,
            @Param("fromDate") LocalDateTime fromDate,
            @Param("toDate") LocalDateTime toDate,
            Pageable pageable
    );

    List<Transaction> findBySenderWalletAndTransactionTypeOrderByCreatedAtDesc(
            Wallet wallet,
            TransactionType type
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.receiverWallet = :wallet
        AND t.transactionType = :type
    """)
    BigDecimal getTotalReceived(
            @Param("wallet") Wallet wallet,
            @Param("type") TransactionType type
    );

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        WHERE t.senderWallet = :wallet
        AND t.transactionType = :type
    """)
    BigDecimal getTotalWithdrawn(
            @Param("wallet") Wallet wallet,
            @Param("type") TransactionType type
    );

    @Query("""
       SELECT new com.billme.payment.dto.MerchantRefundResponse(
            t.invoice.invoiceNumber,
            t.amount,
            t.invoice.paymentMethod,
            t.createdAt,
            t.externalReference
       )
       FROM Transaction t
       WHERE t.senderWallet.id = :walletId
         AND t.transactionType = com.billme.transaction.TransactionType.REFUND
         AND t.status = com.billme.transaction.TransactionStatus.SUCCESS
       ORDER BY t.createdAt DESC
       """)
    List<MerchantRefundResponse> findMerchantRefundHistory(@Param("walletId") Long walletId);

    @Query("""
       SELECT COALESCE(SUM(t.amount), 0)
       FROM Transaction t
       WHERE t.transactionType = :type
       AND t.status = com.billme.transaction.TransactionStatus.SUCCESS
       """)
    BigDecimal sumByTransactionType(@Param("type") TransactionType type);

    @Query("""
        SELECT COALESCE(SUM(t.amount), 0)
        FROM Transaction t
        JOIN t.invoice i
        JOIN i.customer c
        WHERE t.status = com.billme.transaction.TransactionStatus.SUCCESS
        AND t.transactionType IN (
            com.billme.transaction.TransactionType.FACE_PAY,
            com.billme.transaction.TransactionType.UPI_PAY,
            com.billme.transaction.TransactionType.INVOICE_PAYMENT
        )
        AND c.user.id = :userId
    """)
    BigDecimal sumCustomerSpending(@Param("userId") Long userId);

    @Query("""
        SELECT COUNT(DISTINCT t.id)
        FROM Transaction t
        JOIN t.invoice i
        JOIN i.customer c
        WHERE t.status = com.billme.transaction.TransactionStatus.SUCCESS
        AND t.transactionType IN (
            com.billme.transaction.TransactionType.FACE_PAY,
            com.billme.transaction.TransactionType.UPI_PAY,
            com.billme.transaction.TransactionType.INVOICE_PAYMENT
        )
        AND c.user.id = :userId
    """)
    long countSuccessTransactionsByUserId(@Param("userId") Long userId);

    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t WHERE t.status = com.billme.transaction.TransactionStatus.SUCCESS")
    BigDecimal sumTotalRevenue();

    @Query("""
        SELECT COUNT(t) FROM Transaction t 
        WHERE t.receiverWallet.user.id = :userId
        AND t.status = com.billme.transaction.TransactionStatus.SUCCESS
    """)
    long countMerchantTransactions(@Param("userId") Long userId);

    @Query("""
        SELECT t FROM Transaction t
        LEFT JOIN FETCH t.invoice i
        LEFT JOIN FETCH i.customer c
        LEFT JOIN FETCH i.merchant m
        ORDER BY t.createdAt DESC
    """)
    List<Transaction> findAllWithInvoiceDetails();

    @Query("""
SELECT DISTINCT t FROM Transaction t
LEFT JOIN FETCH t.senderWallet sw
LEFT JOIN FETCH sw.user su
LEFT JOIN FETCH t.receiverWallet rw
LEFT JOIN FETCH rw.user ru
LEFT JOIN FETCH t.invoice i
WHERE (
    (sw IS NOT NULL AND su.id = :userId)
    OR
    (rw IS NOT NULL AND ru.id = :userId)
)
AND t.createdAt BETWEEN :startDate AND :endDate
ORDER BY t.createdAt ASC
""")
    List<Transaction> findMerchantStatementTransactions(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate);

    @Query("""
SELECT COALESCE(SUM(
    CASE 
        WHEN t.receiverWallet IS NOT NULL AND t.receiverWallet.user.id = :userId THEN t.amount
        WHEN t.senderWallet IS NOT NULL AND t.senderWallet.user.id = :userId THEN -t.amount
        ELSE 0
    END
), 0)
FROM Transaction t
WHERE 
(
    (t.senderWallet IS NOT NULL AND t.senderWallet.user.id = :userId)
    OR 
    (t.receiverWallet IS NOT NULL AND t.receiverWallet.user.id = :userId)
)
AND t.createdAt < :fromDate
AND t.status = com.billme.transaction.TransactionStatus.SUCCESS
""")
    BigDecimal calculateOpeningBalance(
            @Param("userId") Long userId,
            @Param("fromDate") LocalDateTime fromDate
    );

    // 🛡️ FINANCIAL ANALYTICS: Bucketed Withdrawal Trends (Source: Transactions Table)
    // Using processed_at for business finality and IST (+05:30) for day boundaries.

    @Query(value = "SELECT DATE(CONVERT_TZ(processed_at, '+00:00', '+05:30')) as bucket, COALESCE(SUM(amount), 0) as total FROM transactions " +
                   "WHERE sender_wallet_id = :walletId AND transaction_type = 'WITHDRAWAL' AND status = 'SUCCESS' " +
                   "AND processed_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findDailyWithdrawalTrends(@Param("walletId") Long walletId, 
                                            @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(CONVERT_TZ(processed_at, '+00:00', '+05:30'), '%x-W%v') as bucket, COALESCE(SUM(amount), 0) as total FROM transactions " +
                   "WHERE sender_wallet_id = :walletId AND transaction_type = 'WITHDRAWAL' AND status = 'SUCCESS' " +
                   "AND processed_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findWeeklyWithdrawalTrends(@Param("walletId") Long walletId, 
                                             @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(CONVERT_TZ(processed_at, '+00:00', '+05:30'), '%Y-%m') as bucket, COALESCE(SUM(amount), 0) as total FROM transactions " +
                   "WHERE sender_wallet_id = :walletId AND transaction_type = 'WITHDRAWAL' AND status = 'SUCCESS' " +
                   "AND processed_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findMonthlyWithdrawalTrends(@Param("walletId") Long walletId, 
                                              @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT YEAR(CONVERT_TZ(processed_at, '+00:00', '+05:30')) as bucket, COALESCE(SUM(amount), 0) as total FROM transactions " +
                   "WHERE sender_wallet_id = :walletId AND transaction_type = 'WITHDRAWAL' AND status = 'SUCCESS' " +
                   "AND processed_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findYearlyWithdrawalTrends(@Param("walletId") Long walletId, 
                                             @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}