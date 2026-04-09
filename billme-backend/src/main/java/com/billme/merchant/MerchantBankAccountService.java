package com.billme.merchant;

import com.billme.merchant.dto.BankAccountRequest;
import com.billme.merchant.dto.BankAccountResponse;
import com.billme.repository.MerchantBankAccountRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.UserRepository;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MerchantBankAccountService {

    private final MerchantBankAccountRepository bankAccountRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final UserRepository userRepository;

    private User getLoggedInUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private MerchantProfile getMerchantProfile(User user) {
        return merchantProfileRepository.findByUser_Id(user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Merchant profile not found"));
    }

    @Transactional
    public BankAccountResponse addBankAccount(BankAccountRequest request) {
        User user = getLoggedInUser();
        MerchantProfile profile = getMerchantProfile(user);

        List<MerchantBankAccount> existingAccounts = bankAccountRepository.findByMerchant_User_Id(user.getId());
        
        // Prevent obvious duplicates
        boolean isDuplicate = existingAccounts.stream()
                .anyMatch(a -> a.getAccountNumber().equals(request.getAccountNumber()) 
                        && a.getIfsc().equals(request.getIfsc()));
        if (isDuplicate) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bank account already exists");
        }

        boolean isFirst = existingAccounts.isEmpty();
        boolean makeDefault = isFirst || request.isDefault();

        if (makeDefault && !isFirst) {
            existingAccounts.forEach(acc -> acc.setDefault(false));
            bankAccountRepository.saveAll(existingAccounts);
        }

        MerchantBankAccount newAccount = MerchantBankAccount.builder()
                .merchant(profile)
                .bankName(request.getBankName())
                .accountHolderName(request.getAccountHolderName())
                .accountNumber(request.getAccountNumber())
                .ifsc(request.getIfsc())
                .isDefault(makeDefault)
                .isVerified(false)
                .createdAt(LocalDateTime.now())
                .build();

        bankAccountRepository.save(newAccount);

        if (makeDefault) {
            syncToLegacyProfile(profile, newAccount);
        }

        return mapToResponse(newAccount);
    }

    @Transactional
    public BankAccountResponse setDefaultAccount(Long accountId) {
        User user = getLoggedInUser();
        MerchantProfile profile = getMerchantProfile(user);

        MerchantBankAccount targetAccount = bankAccountRepository.findByIdAndMerchant_User_Id(accountId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

        List<MerchantBankAccount> allAccounts = bankAccountRepository.findByMerchant_User_Id(user.getId());
        for (MerchantBankAccount acc : allAccounts) {
            acc.setDefault(acc.getId().equals(targetAccount.getId()));
        }
        
        bankAccountRepository.saveAll(allAccounts);

        // Proxy Sync to maintain Zero-Regression on Withdrawal/Ledger Modules
        syncToLegacyProfile(profile, targetAccount);

        return mapToResponse(targetAccount);
    }

    @Transactional
    public void deleteBankAccount(Long accountId) {
        User user = getLoggedInUser();
        MerchantBankAccount account = bankAccountRepository.findByIdAndMerchant_User_Id(accountId, user.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bank account not found"));

        if (account.isDefault()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot delete the default bank account. Please set another account as default first.");
        }

        bankAccountRepository.delete(account);
    }

    @Transactional(readOnly = true)
    public List<BankAccountResponse> getBankAccounts() {
        User user = getLoggedInUser();
        return bankAccountRepository.findByMerchant_User_Id(user.getId())
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    /**
     * Forward-sync: called by MerchantProfileService after saving bank fields on the profile.
     * Ensures the profile bank is always visible in merchant_bank_accounts (UI dropdown,
     * withdrawal selection).
     *
     * Idempotency rules:
     *  1. Skip silently if profile bank fields are incomplete.
     *  2. Skip silently if an account with the same accountNumber already exists.
     *  3. Mark as default only if this is the merchant's first bank account.
     *  4. Mark as verified = true (merchant entered it directly on their profile).
     */
    @Transactional
    public void syncFromProfile(MerchantProfile profile) {

        // Guard 1: skip profiles with incomplete bank data
        if (!hasValidBankFields(profile)) return;

        List<MerchantBankAccount> existing = bankAccountRepository
                .findByMerchant_User_Id(profile.getUser().getId());

        // Idempotent Sync: update if account number exists, otherwise create new
        java.util.Optional<MerchantBankAccount> existingMatch = existing.stream()
                .filter(a -> a.getAccountNumber().equals(profile.getAccountNumber()))
                .findFirst();

        if (existingMatch.isPresent()) {
            MerchantBankAccount acc = existingMatch.get();
            acc.setBankName(profile.getBankName());
            acc.setIfsc(profile.getIfscCode());
            String holderName = (profile.getAccountHolderName() != null && !profile.getAccountHolderName().isBlank())
                    ? profile.getAccountHolderName()
                    : profile.getOwnerName();
            acc.setAccountHolderName(holderName);
            bankAccountRepository.save(acc);
            return;
        }

        boolean isFirst = existing.isEmpty();

        // If first bank, de-default any stale records (defensive — should not exist, but safe)
        if (!isFirst) {
            existing.forEach(a -> a.setDefault(false));
            bankAccountRepository.saveAll(existing);
        }

        String holderName = (profile.getAccountHolderName() != null && !profile.getAccountHolderName().isBlank())
                ? profile.getAccountHolderName()
                : profile.getOwnerName();

        MerchantBankAccount profileBank = MerchantBankAccount.builder()
                .merchant(profile)
                .bankName(profile.getBankName())
                .accountHolderName(holderName)
                .accountNumber(profile.getAccountNumber())
                .ifsc(profile.getIfscCode())
                .isDefault(isFirst)   // default only when it is the very first account
                .isVerified(true)     // merchant entered it — treat as verified
                .createdAt(java.time.LocalDateTime.now())
                .build();

        bankAccountRepository.save(profileBank);
    }

    /** Returns true only when all three required bank fields are non-blank. */
    private boolean hasValidBankFields(MerchantProfile profile) {
        return profile.getBankName()      != null && !profile.getBankName().isBlank()
            && profile.getAccountNumber() != null && !profile.getAccountNumber().isBlank()
            && profile.getIfscCode()      != null && !profile.getIfscCode().isBlank();
    }

    // This method is critical for zero regression. Legacy services read from MerchantProfile directly.
    private void syncToLegacyProfile(MerchantProfile profile, MerchantBankAccount defaultAccount) {
        
        // RESCUE SYNC: Before overwriting profile, ensure current data (like SBI) is backed up
        if (hasValidBankFields(profile)) {
            List<MerchantBankAccount> existing = bankAccountRepository.findByMerchant_User_Id(profile.getUser().getId());
            boolean alreadyBackedUp = existing.stream()
                    .anyMatch(a -> a.getAccountNumber().equals(profile.getAccountNumber()));

            if (!alreadyBackedUp) {
                MerchantBankAccount rescueAccount = MerchantBankAccount.builder()
                        .merchant(profile)
                        .bankName(profile.getBankName())
                        .accountHolderName(profile.getAccountHolderName() != null ? profile.getAccountHolderName() : profile.getOwnerName())
                        .accountNumber(profile.getAccountNumber())
                        .ifsc(profile.getIfscCode())
                        .isDefault(false) // Safeguard: Rescued account is NEVER set as default automatically
                        .isVerified(true)
                        .createdAt(java.time.LocalDateTime.now())
                        .build();
                bankAccountRepository.save(rescueAccount);
            }
        }

        profile.setBankName(defaultAccount.getBankName());
        profile.setAccountHolderName(defaultAccount.getAccountHolderName());
        profile.setAccountNumber(defaultAccount.getAccountNumber());
        profile.setIfscCode(defaultAccount.getIfsc());
        merchantProfileRepository.save(profile);
    }

    private BankAccountResponse mapToResponse(MerchantBankAccount account) {
        return BankAccountResponse.builder()
                .id(account.getId())
                .bankName(account.getBankName())
                .accountHolderName(account.getAccountHolderName())
                .accountNumber(account.getAccountNumber())
                .ifsc(account.getIfsc())
                .isDefault(account.isDefault())
                .isVerified(account.isVerified())
                .build();
    }
}
