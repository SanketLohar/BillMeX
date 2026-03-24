package com.billme.email;

import com.billme.invoice.Invoice;

public interface EmailService {

    void sendEmail(String to, String subject, String body);

    void sendCustomerPaymentSuccessEmail(Invoice invoice);

    void sendMerchantPaymentReceivedEmail(Invoice invoice);

    void sendRefundCompletedEmail(String to, String invoiceNumber);
}