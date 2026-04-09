package com.billme.merchant;

import com.billme.merchant.dto.MerchantProfileResponse;
import com.billme.merchant.dto.MerchantProfileUpdateRequest;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
@RequiredArgsConstructor
public class MerchantProfileService {

    private final MerchantProfileRepository merchantProfileRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    @Lazy
    private final MerchantBankAccountService merchantBankAccountService;

    // ================= GET AUTHENTICATED USER =================
    private User getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    // ================= GET PROFILE =================
    @Transactional(readOnly = true)
    public MerchantProfileResponse getProfile() {

        User user = getLoggedInUser();

        MerchantProfile profile = merchantProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found"));

        return mapToResponse(profile);
    }

    // ================= UPDATE PROFILE =================
    @Transactional
    public MerchantProfileResponse updateProfile(MerchantProfileUpdateRequest request) {

        User user = getLoggedInUser();

        MerchantProfile profile = merchantProfileRepository
                .findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found"));

        Wallet wallet = walletRepository.findByUser_Id(user.getId())
                .orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.NOT_FOUND, "Wallet not found"));

        // ================= BASIC DETAILS =================
        // businessName and ownerName are read-only per audit rules
        
        if (request.getPhone() != null)
            profile.setPhone(request.getPhone());

        if (request.getAddress() != null)
            profile.setAddress(request.getAddress());

        if (request.getState() != null)
            profile.setState(request.getState());

        if (request.getCity() != null)
            profile.setCity(request.getCity());

        if (request.getPinCode() != null)
            profile.setPinCode(request.getPinCode());

        if (request.getUpiId() != null)
            profile.setUpiId(request.getUpiId());

        // ================= GST VALIDATION =================

        if (profile.getGstin() != null && !profile.getGstin().isBlank()) {

            // GSTIN already saved → lock editing
            if (request.getGstin() != null &&
                    !request.getGstin().equals(profile.getGstin())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "GSTIN cannot be changed after initial registration"
                );
            }

        } else {

            // first time GSTIN save allowed
            if (request.isGstRegistered()) {

                if (request.getGstin() == null || request.getGstin().isBlank()) {
                    throw new ResponseStatusException(
                            HttpStatus.BAD_REQUEST,
                            "GSTIN required when GST registered"
                    );
                }

                profile.setGstRegistered(true);
                profile.setGstin(request.getGstin());
            }
        }

        // ================= BANK ACCOUNT LOCK =================
        // If merchant already has funds/transactions → bank account cannot change

        boolean hasSettlementStarted = wallet.getBalance().compareTo(java.math.BigDecimal.ZERO) > 0
                || wallet.getEscrowBalance().compareTo(java.math.BigDecimal.ZERO) > 0;

// ================= BANK ACCOUNT SAFE UPDATE =================

        if (request.getBankName() != null) {
            profile.setBankName(request.getBankName());
        }

        if (request.getAccountHolderName() != null) {
            profile.setAccountHolderName(request.getAccountHolderName());
        }

// 🔐 ACCOUNT NUMBER LOGIC
        if (request.getAccountNumber() != null) {

            if (hasSettlementStarted &&
                    profile.getAccountNumber() != null &&
                    !profile.getAccountNumber().equals(request.getAccountNumber())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Account number cannot be changed after payments"
                );
            }

            profile.setAccountNumber(request.getAccountNumber());
        }

// 🔐 IFSC LOGIC
        if (request.getIfscCode() != null) {

            if (hasSettlementStarted &&
                    profile.getIfscCode() != null &&
                    !profile.getIfscCode().equals(request.getIfscCode())) {

                throw new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "IFSC cannot be changed after payments"
                );
            }

            profile.setIfscCode(request.getIfscCode());
        }

        // ================= PROFILE COMPLETION VALIDATION =================

        boolean profileCompleted =
                isValid(profile.getBusinessName()) &&
                        isValid(profile.getOwnerName()) &&
                        isValid(profile.getPhone()) &&
                        isValid(profile.getAddress()) &&
                        isValid(profile.getCity()) &&
                        isValid(profile.getState()) &&
                        isValid(profile.getPinCode()) &&
                        isValid(profile.getUpiId()) &&
                        isValid(profile.getBankName()) &&
                        isValid(profile.getAccountNumber()) &&
                        isValid(profile.getIfscCode());

        profile.setProfileCompleted(profileCompleted);

        merchantProfileRepository.save(profile);

        // Forward-sync: ensure profile bank is visible in merchant_bank_accounts
        // (withdrawal UI, bank dropdown). Idempotent — safe on every save.
        merchantBankAccountService.syncFromProfile(profile);

        return mapToResponse(profile);
    }

    // ================= UTILITY VALIDATION =================
    private boolean isValid(String value) {
        return value != null && !value.trim().isEmpty();
    }

    // ================= DTO MAPPING =================
    private MerchantProfileResponse mapToResponse(MerchantProfile profile) {

        return MerchantProfileResponse.builder()
                .businessName(profile.getBusinessName())
                .ownerName(profile.getOwnerName())
                .email(profile.getUser().getEmail()) // Include email
                .phone(profile.getPhone())
                .address(profile.getAddress())
                .state(profile.getState())
                .city(profile.getCity())
                .pinCode(profile.getPinCode())
                .upiId(profile.getUpiId())
                .bankName(profile.getBankName())
                .accountHolderName(profile.getAccountHolderName())
                .accountNumber(profile.getAccountNumber())
                .ifscCode(profile.getIfscCode())
                .profileCompleted(profile.isProfileCompleted())
                .gstRegistered(profile.isGstRegistered())
                .gstin(profile.getGstin())
                .profileImageUrl(profile.getUser().getProfileImageUrl())
                .build();
    }
}