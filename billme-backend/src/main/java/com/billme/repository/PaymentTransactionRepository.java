package com.billme.repository;

import com.billme.payment.PaymentTransaction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.List;


public interface PaymentTransactionRepository
        extends JpaRepository<PaymentTransaction, Long> {

    Optional<PaymentTransaction>
    findByRazorpayPaymentId(String razorpayPaymentId);


    List<PaymentTransaction> findByInvoice_Customer_User_Email(String email);

    List<PaymentTransaction> findByInvoice_Merchant_User_Email(String email);
}