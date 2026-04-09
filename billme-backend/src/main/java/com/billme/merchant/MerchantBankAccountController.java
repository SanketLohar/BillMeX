package com.billme.merchant;

import com.billme.merchant.dto.BankAccountRequest;
import com.billme.merchant.dto.BankAccountResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.util.List;

@RestController
@RequestMapping("/api/v1/merchant/bank-accounts")
@RequiredArgsConstructor
public class MerchantBankAccountController {

    private final MerchantBankAccountService bankAccountService;

    @PostMapping
    public ResponseEntity<BankAccountResponse> addBankAccount(@Valid @RequestBody BankAccountRequest request) {
        return ResponseEntity.ok(bankAccountService.addBankAccount(request));
    }

    @GetMapping
    public ResponseEntity<List<BankAccountResponse>> getBankAccounts() {
        return ResponseEntity.ok(bankAccountService.getBankAccounts());
    }

    @PutMapping("/{id}/default")
    public ResponseEntity<BankAccountResponse> setDefaultAccount(@PathVariable Long id) {
        return ResponseEntity.ok(bankAccountService.setDefaultAccount(id));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteBankAccount(@PathVariable Long id) {
        bankAccountService.deleteBankAccount(id);
        return ResponseEntity.noContent().build();
    }
}
