package com.billme.invoice.dto;

import java.math.BigDecimal;
import java.util.List;

public class PublicInvoiceResponse {

    private Long id;
    private String invoiceNumber;
    private String merchantName;
    private String merchantGSTIN;
    private String customerEmail;
    private List<InvoiceItemResponse> items;
    private BigDecimal subtotal;
    private BigDecimal cgstTotal;
    private BigDecimal sgstTotal;
    private BigDecimal igstTotal;
    private BigDecimal gstTotal;
    private BigDecimal processingFee;
    private BigDecimal totalPayable;
    private String status;

    public PublicInvoiceResponse(Long id,
                                 String invoiceNumber,
                                 String merchantName,
                                 String merchantGSTIN,
                                 String customerEmail,
                                 List<InvoiceItemResponse> items,
                                 BigDecimal subtotal,
                                 BigDecimal cgstTotal,
                                 BigDecimal sgstTotal,
                                 BigDecimal igstTotal,
                                 BigDecimal gstTotal,
                                 BigDecimal processingFee,
                                 BigDecimal totalPayable,
                                 String status) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.merchantName = merchantName;
        this.merchantGSTIN = merchantGSTIN;
        this.customerEmail = customerEmail;
        this.items = items;
        this.subtotal = subtotal;
        this.cgstTotal = cgstTotal;
        this.sgstTotal = sgstTotal;
        this.igstTotal = igstTotal;
        this.gstTotal = gstTotal;
        this.processingFee = processingFee;
        this.totalPayable = totalPayable;
        this.status = status;
    }

    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getMerchantName() { return merchantName; }
    public String getMerchantGSTIN() { return merchantGSTIN; }
    public String getCustomerEmail() { return customerEmail; }
    public List<InvoiceItemResponse> getItems() { return items; }
    public BigDecimal getSubtotal() { return subtotal; }
    public BigDecimal getCgstTotal() { return cgstTotal; }
    public BigDecimal getSgstTotal() { return sgstTotal; }
    public BigDecimal getIgstTotal() { return igstTotal; }
    public BigDecimal getGstTotal() { return gstTotal; }
    public BigDecimal getProcessingFee() { return processingFee; }
    public BigDecimal getTotalPayable() { return totalPayable; }
    public String getStatus() { return status; }
}