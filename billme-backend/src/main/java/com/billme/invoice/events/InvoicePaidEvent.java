package com.billme.invoice.events;

import com.billme.invoice.Invoice;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

@Getter
public class InvoicePaidEvent extends ApplicationEvent {

    private final Invoice invoice;

    public InvoicePaidEvent(Object source, Invoice invoice) {
        super(source);
        this.invoice = invoice;
    }
}
