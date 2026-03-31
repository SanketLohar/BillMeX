package com.billme.invoice;

import com.billme.invoice.dto.InvoiceItemResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.time.format.DateTimeFormatter;
import java.util.stream.Collectors;
import com.billme.util.NumberToWords;
@Service
@RequiredArgsConstructor
public class InvoiceTemplateService {

    private final TemplateEngine templateEngine;

    @Value("${app.frontend.url}")
    private String frontendUrl;

    public String generateInvoiceHtml(Invoice invoice) {

        Context context = new Context();

        DateTimeFormatter formatter =
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm");

        // ===== BASIC =====
        context.setVariable("invoiceNumber", invoice.getInvoiceNumber());

        context.setVariable("invoiceDate",
                invoice.getIssuedAt() != null
                        ? invoice.getIssuedAt().format(formatter)
                        : "");

        context.setVariable("dueDate",
                invoice.getDueDate() != null
                        ? invoice.getDueDate().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                        : "N/A");

        context.setVariable("status",
                invoice.getStatus() != null
                        ? invoice.getStatus().name()
                        : "");

        context.setVariable("paymentMethod",
                invoice.getPaymentMethod() != null
                        ? invoice.getPaymentMethod().name().replace('_', ' ')
                        : "—");

        context.setVariable("paidDate",
                (invoice.getStatus() != null && invoice.getStatus().name().equals("PAID") && invoice.getPaidAt() != null)
                        ? invoice.getPaidAt().format(formatter)
                        : null);

        // ===== MERCHANT =====
        context.setVariable("merchantName", invoice.getMerchant().getBusinessName());
        context.setVariable("merchantAddress", invoice.getMerchant().getAddress());
        context.setVariable("merchantGstin", invoice.getMerchant().getGstin());

        // ✅ BANK DETAILS
        context.setVariable("bankName", invoice.getMerchant().getBankName());
        context.setVariable("accountHolder", invoice.getMerchant().getAccountHolderName());
        context.setVariable("accountNumber", invoice.getMerchant().getAccountNumber());
        context.setVariable("ifsc", invoice.getMerchant().getIfscCode());

        // ✅ UPI
        context.setVariable("upiId", invoice.getMerchant().getUpiId());

        // ===== CUSTOMER =====
        context.setVariable("customerName", invoice.getResolvedCustomerName());
        context.setVariable("customerEmail", invoice.getResolvedCustomerEmail());

        // ===== TOTALS =====
        context.setVariable("subtotal", invoice.getSubtotal());
        context.setVariable("cgst", safe(invoice.getCgstTotal()));
        context.setVariable("sgst", safe(invoice.getSgstTotal()));
        context.setVariable("igst", safe(invoice.getIgstTotal()));
        context.setVariable("processingFee", safe(invoice.getProcessingFee()));
        context.setVariable("totalPayable", invoice.getTotalPayable());

        boolean isIntraState =
                invoice.getIgstTotal() == null ||
                        invoice.getIgstTotal().compareTo(java.math.BigDecimal.ZERO) == 0;

        context.setVariable("isIntraState", isIntraState);
        context.setVariable("gstRegistered", invoice.getMerchant().isGstRegistered());

        // ===== ITEMS =====
        context.setVariable(
                "items",
                invoice.getItems().stream()
                        .map(item ->
                                InvoiceItemResponse.builder()
                                        .productName(item.getProductNameSnapshot())
                                        .quantity(item.getQuantity())
                                        .unitPrice(item.getUnitPrice())
                                        .gstRate(item.getGstRate())
                                        .gstAmount(item.getGstAmount())
                                        .totalPrice(item.getTotalPrice())
                                        .build()
                        )
                        .collect(Collectors.toList())
        );
        context.setVariable("amountInWords",
                NumberToWords.convert(invoice.getTotalPayable())
        );

        // ===== PAY LINK =====
        context.setVariable(
                "payUrl",
                frontendUrl + "/pay-invoice.html?num="
                        + invoice.getInvoiceNumber()
                        + "&token=" + invoice.getPaymentToken()
        );

        return templateEngine.process("invoice-template", context);
    }

    private java.math.BigDecimal safe(java.math.BigDecimal value) {
        return value != null ? value : java.math.BigDecimal.ZERO;
    }

}