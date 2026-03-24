package com.billme.transaction;

import com.billme.transaction.dto.TransactionResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/transactions")
@RequiredArgsConstructor
public class TransactionLedgerController {

    private final TransactionLedgerService ledgerService;

    @GetMapping("/customer")
    public List<TransactionResponse> customerLedger() {
        return ledgerService.getCustomerTransactions();
    }

    @GetMapping("/merchant")
    public List<TransactionResponse> merchantLedger() {
        return ledgerService.getMerchantTransactions();
    }
}