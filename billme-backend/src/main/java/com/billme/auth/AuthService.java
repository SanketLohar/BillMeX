package com.billme.auth;

import com.billme.auth.token.RefreshToken;
import com.billme.auth.token.RefreshTokenService;
import com.billme.customer.CustomerProfile;
import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.repository.WalletRepository;
import com.billme.security.jwt.JwtService;
import com.billme.user.Role;
import com.billme.user.User;
import com.billme.user.UserResponse;
import com.billme.wallet.Wallet;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.Period;
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final TransactionRepository transactionRepository;

    // ================= CUSTOMER REGISTER =================
    @Transactional
    public AuthResponse registerCustomer(CustomerRegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Email already registered"
            );
        }

        if (request.getFaceEmbeddings() == null || request.getFaceEmbeddings().isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Face embedding is required"
            );
        }

        if (customerProfileRepository.findByFaceEmbeddings(request.getFaceEmbeddings()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Face already registered"
            );
        }

        if (request.getDob() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date of birth is required"
            );
        }

        int age = Period.between(request.getDob(), LocalDate.now()).getYears();

        if (age < 18) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User must be at least 18 years old"
            );
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.CUSTOMER)
                .active(true)
                .build();

        userRepository.save(user);

        CustomerProfile profile = CustomerProfile.builder()
                .user(user)
                .name(request.getName())
                .dob(request.getDob())
                .contactNumber(request.getContactNumber())
                .address(request.getAddress())
                .state(request.getState())
                .city(request.getCity())
                .pinCode(request.getPinCode())
                .faceEmbeddings(request.getFaceEmbeddings())
                .build();

        customerProfileRepository.save(profile);

        Wallet wallet = Wallet.builder()
                .user(user)
                .balance(BigDecimal.ZERO)
                .escrowBalance(BigDecimal.ZERO)
                .build();

        walletRepository.save(wallet);

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getRole().name(),
                user.getId()
        );
    }


    // ================= MERCHANT REGISTER =================
    @Transactional
    public AuthResponse registerMerchant(MerchantRegisterRequest request) {

        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Merchant already registered"
            );
        }

        if (request.getDob() == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Date of birth is required"
            );
        }

        int age = Period.between(request.getDob(), LocalDate.now()).getYears();

        if (age < 18) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Merchant must be at least 18 years old"
            );
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .role(Role.MERCHANT)
                .active(true)
                .build();

        userRepository.save(user);

        merchantProfileRepository.save(
                com.billme.merchant.MerchantProfile.builder()
                        .user(user)
                        .businessName(request.getBusinessName())
                        .ownerName(request.getOwnerName())
                        .profileCompleted(false)
                        .build()
        );

        walletRepository.save(
                Wallet.builder()
                        .user(user)
                        .balance(BigDecimal.ZERO)
                        .escrowBalance(BigDecimal.ZERO)
                        .build()
        );

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken = refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getRole().name(),
                user.getId()
        );
    }

    // ================= LOGIN =================
//    @Transactional


    public AuthResponse login(LoginRequest request) {

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        // 🔒 Only protect ADMIN
        if (user.getRole() == Role.ADMIN) {

            // Check if locked
            if (user.getLockUntil() != null &&
                    user.getLockUntil().isAfter(LocalDateTime.now())) {

                throw new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "Account locked. Try again later."
                );
            }
        }

        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // ✅ If success → reset counters
            user.setFailedAttempts(0);
            user.setLockUntil(null);
            
            // 🕒 Throttled lastActive update (5 mins)
            if (user.getLastActive() == null || 
                user.getLastActive().isBefore(LocalDateTime.now().minusMinutes(5))) {
                user.setLastActive(LocalDateTime.now());
            }
            
            userRepository.save(user);

        } catch (Exception ex) {

            if (user.getRole() == Role.ADMIN) {

                int attempts = user.getFailedAttempts() + 1;
                user.setFailedAttempts(attempts);

                if (attempts >= 5) {
                    user.setLockUntil(LocalDateTime.now().plusMinutes(15));
                }

                userRepository.save(user);
            }

            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Bad credentials"
            );
        }

        String accessToken = jwtService.generateAccessToken(
                user.getEmail(),
                user.getRole().name()
        );

        RefreshToken refreshToken =
                refreshTokenService.createRefreshToken(user);

        return new AuthResponse(
                accessToken,
                refreshToken.getToken(),
                user.getRole().name(),
                user.getId()
        );
    }

    public com.billme.user.UserResponse getMe(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "User not found"
                ));

        return enrichUserResponse(user);
    }

    public UserResponse getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return enrichUserResponse(user);
    }

    private UserResponse enrichUserResponse(User user) {
        String name = "Unknown";
        if (user.getRole() == Role.CUSTOMER) {
            name = customerProfileRepository.findByUser_Id(user.getId())
                    .map(com.billme.customer.CustomerProfile::getName)
                    .orElse("Customer");
        } else if (user.getRole() == Role.MERCHANT) {
            name = merchantProfileRepository.findByUser_Id(user.getId())
                    .map(com.billme.merchant.MerchantProfile::getOwnerName)
                    .orElse("Merchant");
        } else if (user.getRole() == Role.ADMIN) {
            name = "Administrator";
        }

        com.billme.user.UserStats stats = com.billme.user.UserStats.builder()
                .totalSpend(transactionRepository.sumCustomerSpending(user.getId()))
                .totalTransactions(transactionRepository.countSuccessTransactionsByUserId(user.getId()))
                .build();

        return com.billme.user.UserResponse.builder()
                .id(user.getId())
                .email(user.getEmail())
                .role(user.getRole().name())
                .profileImageUrl(user.getProfileImageUrl())
                .createdAt(user.getCreatedAt())
                .active(user.isActive())
                .name(name)
                .lastActive(user.getLastActive())
                .stats(stats)
                .build();
    }
}
