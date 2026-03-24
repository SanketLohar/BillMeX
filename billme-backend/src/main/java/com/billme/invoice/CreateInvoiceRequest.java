package com.billme.invoice;

import com.billme.invoice.dto.CreateInvoiceItemRequest;
import lombok.Data;
import java.util.List;

@Data
public class CreateInvoiceRequest {

    private String customerEmail;
    
    private String customerName;

    private String customerState;

    private List<CreateInvoiceItemRequest> items;
}
