package com.billme.payment;

import com.billme.payment.dto.WithdrawalRequest;
import com.billme.payment.dto.WithdrawalResponse;
import com.billme.wallet.dto.WalletSummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/merchant")
@RequiredArgsConstructor
public class WithdrawalController {

    private final WithdrawalService withdrawalService;

    @PostMapping("/withdraw")
    public ResponseEntity<String> withdraw(
            @RequestBody WithdrawalRequest request) {

        withdrawalService.withdraw(request.getAmount());

        return ResponseEntity.ok("Withdrawal successful (Simulated)");
    }
    @GetMapping("/withdrawals")
    public ResponseEntity<List<WithdrawalResponse>> getWithdrawals() {

        return ResponseEntity.ok(
                withdrawalService.getWithdrawalHistory()
        );
    }
    @GetMapping("/wallet")
    public ResponseEntity<WalletSummaryResponse> getWallet() {
        return getWalletSummary();
    }

    @GetMapping("/wallet/summary")
    public ResponseEntity<WalletSummaryResponse> getWalletSummary() {

        return ResponseEntity.ok(
                withdrawalService.getWalletSummary()
        );
    }

}