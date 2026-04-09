package com.billme.payment;

import com.billme.payment.dto.MerchantRefundResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/refund")
public class RefundController {

    private final RefundService refundService;
    private final RefundTokenService refundTokenService;

    @PostMapping("/{invoiceId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public String approveRefundMerchant(@PathVariable Long invoiceId) {
        refundService.refundInvoice(invoiceId);
        return "Refund successful";
    }

    @PostMapping("/reject/{invoiceId}")
    @PreAuthorize("hasRole('MERCHANT')")
    public String rejectRefundMerchant(@PathVariable Long invoiceId) {
        refundService.rejectRefund(invoiceId);
        return "Refund rejected";
    }

    @PostMapping("/request/{invoiceId}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public String requestRefund(@PathVariable Long invoiceId, @RequestBody Map<String, String> request) {
        refundService.requestRefund(invoiceId, request.get("refundReason"), request.get("refundCategory"));
        return "Refund requested successfully";
    }

    @GetMapping("/email/approve/{token}")
    public String approveRefundEmail(@PathVariable String token) {
        try {
            if (!refundTokenService.isTokenValid(token)) {
                return renderStatusPage("Invalid link", "This refund link is invalid or has already been used.", false);
            }
            
            Long invoiceId = refundTokenService.extractInvoiceId(token);
            Long merchantId = refundTokenService.extractMerchantId(token);
            
            refundService.validateAndProcessRefund(invoiceId, merchantId, true);
            
            return renderStatusPage("Refund Approved", "The refund has been processed successfully. The funds will be credited to the customer shortly.", true);
        } catch (Exception e) {
            return renderStatusPage("Approval Failed", e.getMessage(), false);
        }
    }

    @GetMapping("/email/reject/{token}")
    public String rejectRefundEmail(@PathVariable String token) {
        try {
            if (!refundTokenService.isTokenValid(token)) {
                return renderStatusPage("Invalid link", "This refund link is invalid or has already been used.", false);
            }
            
            Long invoiceId = refundTokenService.extractInvoiceId(token);
            Long merchantId = refundTokenService.extractMerchantId(token);
            
            refundService.validateAndProcessRefund(invoiceId, merchantId, false);
            
            return renderStatusPage("Refund Rejected", "The refund request has been declined. No funds were transferred.", true);
        } catch (Exception e) {
            return renderStatusPage("Rejection Failed", e.getMessage(), false);
        }
    }

    private String renderStatusPage(String title, String message, boolean success) {
        String icon = success ? "fa-check-circle" : "fa-exclamation-circle";
        String color = success ? "#34a853" : "#ea4335";
        
        return "<!DOCTYPE html><html><head>" +
               "<link rel='stylesheet' href='https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css'>" +
               "<link href='https://fonts.googleapis.com/css2?family=Inter:wght@400;600;800&display=swap' rel='stylesheet'>" +
               "<style>" +
               "body { font-family: 'Inter', sans-serif; display: flex; align-items: center; justify-content: center; height: 100vh; margin: 0; background: #f8f9fa; color: #3c4043; }" +
               ".card { background: white; padding: 48px; border-radius: 24px; box-shadow: 0 10px 30px rgba(0,0,0,0.05); text-align: center; max-width: 400px; width: 90%; }" +
               ".icon { font-size: 64px; color: " + color + "; margin-bottom: 24px; }" +
               "h1 { margin: 0 0 16px; font-weight: 800; font-size: 24px; }" +
               "p { margin: 0; line-height: 1.6; color: #5f6368; }" +
               ".btn { display: inline-block; margin-top: 32px; padding: 12px 32px; background: #1a73e8; color: white; text-decoration: none; border-radius: 8px; font-weight: 600; }" +
               "</style></head><body>" +
               "<div class='card'>" +
               "<div class='icon'><i class='fas " + icon + "'></i></div>" +
               "<h1>" + title + "</h1>" +
               "<p>" + message + "</p>" +
               "<a href='#' onclick='window.close()' class='btn'>Close Tab</a>" +
               "</div></body></html>";
    }

    @GetMapping("/merchant/refunds")
    @PreAuthorize("hasRole('MERCHANT')")
    public ResponseEntity<List<MerchantRefundResponse>> getMerchantRefundHistory(
            Authentication authentication
    ) {
        String email = authentication.getName();
        List<MerchantRefundResponse> refunds =
                refundService.getMerchantRefundHistory(email);

        return ResponseEntity.ok(refunds);
    }
}