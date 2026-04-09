package com.billme.report;

import com.billme.report.util.DateRangeUtils;


import com.billme.merchant.MerchantProfile;
import com.billme.report.dto.ProfitLossResponse;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/merchant/profit-loss")
@RequiredArgsConstructor
public class ProfitLossController {

    private final ProfitLossService profitLossService;
    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;

    private MerchantProfile getLoggedInMerchant() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));
        return merchantProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new RuntimeException("Merchant profile not found"));
    }

    @GetMapping
    public ResponseEntity<ProfitLossResponse> getProfitLoss(
            @RequestParam(required = false) String range,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate) {
        
        DateRangeUtils.DateRange resolved = DateRangeUtils.resolveRange(range, startDate, endDate);
        return ResponseEntity.ok(profitLossService.calculateProfitLoss(getLoggedInMerchant(), resolved.getStartDate(), resolved.getEndDate()));
    }

}
