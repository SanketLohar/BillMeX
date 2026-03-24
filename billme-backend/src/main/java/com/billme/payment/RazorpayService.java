package com.billme.payment;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceStatus;
import com.billme.repository.InvoiceRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class RazorpayService {

    @Value("${razorpay.key-id}")
    private String keyId;

    @Value("${razorpay.key-secret}")
    private String keySecret;

    private final InvoiceRepository invoiceRepository;

    public Order createOrder(Invoice invoice) {

        try {

            // 🔒 1. Validate invoice status
            if (invoice.getStatus() == InvoiceStatus.PAID) {
                throw new RuntimeException("Invoice already paid");
            }

            // 🔁 2. Prevent duplicate order creation
            if (invoice.getRazorpayOrderId() != null) {
                throw new RuntimeException("Order already created for this invoice");
            }

            RazorpayClient razorpayClient =
                    new RazorpayClient(keyId, keySecret);

            JSONObject options = new JSONObject();

            // Convert ₹ to paise
            int amountInPaise = invoice.getTotalPayable()
                    .multiply(BigDecimal.valueOf(100))
                    .intValue();

            options.put("amount", amountInPaise);
            options.put("currency", "INR");
            options.put("receipt", invoice.getInvoiceNumber());

            Order order = razorpayClient.orders.create(options);

            // 💾 3. Persist Razorpay order ID
            invoice.setRazorpayOrderId(order.get("id"));
            invoiceRepository.save(invoice);

            return order;

        } catch (Exception e) {
            throw new RuntimeException("Failed to create Razorpay order: " + e.getMessage());
        }


    }
    public void refundPayment(String paymentId, BigDecimal amount) {

        try {

            RazorpayClient razorpayClient =
                    new RazorpayClient(keyId, keySecret);

            JSONObject refundRequest = new JSONObject();
            refundRequest.put("amount",
                    amount.multiply(BigDecimal.valueOf(100)).intValue());

            razorpayClient.payments.refund(paymentId, refundRequest);

        } catch (Exception e) {
            throw new RuntimeException("Refund failed: " + e.getMessage());
        }
    }
}