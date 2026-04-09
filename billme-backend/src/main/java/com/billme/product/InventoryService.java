package com.billme.product;

import com.billme.invoice.Invoice;
import com.billme.invoice.InvoiceItem;
import com.billme.invoice.events.InvoicePaidEvent;
import com.billme.invoice.events.InvoiceRefundedEvent;
import com.billme.repository.ProductRepository;
import com.billme.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleInvoicePaid(InvoicePaidEvent event) {
        Invoice invoice = event.getInvoice();
        String refId = "INV-PAID-" + invoice.getId();
        
        Optional<StockMovement> existing = stockMovementRepository.findByReferenceIdAndMovementType(refId, StockMovement.MovementType.OUT);
        if (existing.isPresent()) {
            log.info("Idempotency match: Stock already deduced for invoice {}", invoice.getId());
            return;
        }

        try {
            for (InvoiceItem item : invoice.getItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    processStockDeduction(product, item.getQuantity(), refId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to deduce stock for Invoice {}: {}", invoice.getId(), e.getMessage());
            // Since this runs AFTER_COMMIT of the main transaction, throwing here won't rollback the payment.
            // This achieves zero-regression isolation.
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleInvoiceRefunded(InvoiceRefundedEvent event) {
        Invoice invoice = event.getInvoice();
        String refId = "INV-RFND-" + invoice.getId();
        
        Optional<StockMovement> existing = stockMovementRepository.findByReferenceIdAndMovementType(refId, StockMovement.MovementType.IN);
        if (existing.isPresent()) {
            log.info("Idempotency match: Stock already restocked for refunded invoice {}", invoice.getId());
            return;
        }

        try {
            for (InvoiceItem item : invoice.getItems()) {
                Product product = item.getProduct();
                if (product != null) {
                    processStockRestock(product, item.getQuantity(), refId);
                }
            }
        } catch (Exception e) {
            log.error("Failed to restock for Refunded Invoice {}: {}", invoice.getId(), e.getMessage());
        }
    }

    private void processStockDeduction(Product product, Integer quantity, String refId) {
        int currentStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        int newStock = currentStock - quantity;

        if (newStock < 0) {
            if (!product.getMerchant().isAllowNegativeStock()) {
                throw new RuntimeException("Negative stock not allowed for product: " + product.getId());
            } else {
                log.warn("⚠️ [INVENTORY WARNING] Product {} ({}) has fallen into negative stock ({}). Merchant has AllowNegativeStock enabled.", 
                         product.getId(), product.getName(), newStock);
            }
        }

        product.setStockQuantity(newStock);
        productRepository.save(product);

        StockMovement movement = StockMovement.builder()
                .product(product)
                .quantity(quantity)
                .movementType(StockMovement.MovementType.OUT)
                .referenceId(refId + "-P" + product.getId()) // Ensure uniqueness per product
                .reason("INVOICE_PAYMENT")
                .createdAt(LocalDateTime.now())
                .build();
        stockMovementRepository.save(movement);
    }

    private void processStockRestock(Product product, Integer quantity, String refId) {
        int currentStock = product.getStockQuantity() == null ? 0 : product.getStockQuantity();
        product.setStockQuantity(currentStock + quantity);
        productRepository.save(product);

        StockMovement movement = StockMovement.builder()
                .product(product)
                .quantity(quantity)
                .movementType(StockMovement.MovementType.IN)
                .referenceId(refId + "-P" + product.getId())
                .reason("INVOICE_REFUND")
                .createdAt(LocalDateTime.now())
                .build();
        stockMovementRepository.save(movement);
    }
}
