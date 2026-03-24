package com.billme.customer;

import com.billme.customer.dto.CustomerLookupResponse;
import com.billme.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/customer")
@RequiredArgsConstructor
public class CustomerController {

    private final CustomerProfileRepository customerProfileRepository;

    // ==========================================
    // CUSTOMER PROFILE
    // ==========================================
    @GetMapping("/profile")
    @PreAuthorize("hasRole('CUSTOMER')")
    public CustomerLookupResponse getCustomerProfile(Authentication authentication) {

        String email = authentication.getName();

        CustomerProfile profile = customerProfileRepository
                .findByUser_Email(email)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        return CustomerLookupResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .email(profile.getUser().getEmail())
                .profileImageUrl(profile.getUser().getProfileImageUrl())
                .build();
    }

    // ==========================================
    // LOOKUP CUSTOMER BY EMAIL
    // ==========================================
    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('MERCHANT')")
    public CustomerLookupResponse findCustomerByEmail(@PathVariable String email) {

        CustomerProfile profile = customerProfileRepository
                .findByUser_Email(email)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        return CustomerLookupResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .email(profile.getUser().getEmail())
                .build();
    }
}