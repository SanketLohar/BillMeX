package com.billme.admin;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final AdminService adminService;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        return ResponseEntity.ok(adminService.getStats());
    }

    @GetMapping("/transactions")
    public ResponseEntity<List<?>> getTransactions() {
        return ResponseEntity.ok(adminService.getTransactions());
    }

    @GetMapping("/revenue")
    public ResponseEntity<List<?>> getRevenueBreakdown() {
        return ResponseEntity.ok(adminService.getRevenueBreakdown());
    }

    @GetMapping("/merchants")
    public ResponseEntity<List<?>> getMerchants() {
        return ResponseEntity.ok(adminService.getMerchants());
    }

    @GetMapping("/customers")
    public ResponseEntity<List<?>> getCustomers() {
        return ResponseEntity.ok(adminService.getCustomers());
    }

    @PutMapping("/merchant/{id}/approve")
    public ResponseEntity<Void> approveMerchant(@PathVariable Long id) {
        adminService.updateMerchantStatus(id, true);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/merchant/{id}/suspend")
    public ResponseEntity<Void> suspendMerchant(@PathVariable Long id) {
        adminService.updateMerchantStatus(id, false);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/fraud-check")
    public ResponseEntity<List<?>> getFraudAlerts() {
        return ResponseEntity.ok(adminService.getFraudAlerts());
    }

    @GetMapping("/merchants/{id}")
    public ResponseEntity<Map<String, Object>> getMerchantDetails(@PathVariable Long id) {
        return ResponseEntity.ok(adminService.getMerchantDetails(id));
    }
}