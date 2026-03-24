package com.billme.merchant;

import com.billme.merchant.dto.MerchantProfileResponse;
import com.billme.merchant.dto.MerchantProfileUpdateRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchant/profile")
@RequiredArgsConstructor
@PreAuthorize("hasRole('MERCHANT')")
public class MerchantProfileController {

    private final MerchantProfileService merchantProfileService;

    /**
     * Get current merchant profile
     */
    @GetMapping
    public ResponseEntity<MerchantProfileResponse> getProfile() {
        MerchantProfileResponse response = merchantProfileService.getProfile();
        return ResponseEntity.ok(response);
    }

    /**
     * Update merchant profile
     */
    @PutMapping
    public ResponseEntity<MerchantProfileResponse> updateProfile(
            @Valid @RequestBody MerchantProfileUpdateRequest request) {

        MerchantProfileResponse response =
                merchantProfileService.updateProfile(request);

        return ResponseEntity.ok(response);
    }
}