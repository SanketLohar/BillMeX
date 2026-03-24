package com.billme.admin;

import com.billme.repository.CustomerProfileRepository;
import com.billme.repository.MerchantProfileRepository;
import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.transaction.Transaction;
import com.billme.user.Role;
import com.billme.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class AdminService {

    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final MerchantProfileRepository merchantProfileRepository;
    private final CustomerProfileRepository customerProfileRepository;

    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalMerchants", userRepository.countByRole(Role.MERCHANT));
        stats.put("totalCustomers", userRepository.countByRole(Role.CUSTOMER));
        stats.put("activeUsers", userRepository.countByActive(true));
        stats.put("totalTransactions", transactionRepository.count());
        stats.put("totalRevenue", transactionRepository.sumTotalRevenue());
        return stats;
    }

    public List<Map<String, Object>> getTransactions() {
        List<Transaction> transactions = transactionRepository.findAllWithInvoiceDetails();
        return transactions.stream().map(t -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", t.getId() != null ? t.getId() : 0);
            map.put("amount", t.getAmount() != null ? t.getAmount() : BigDecimal.ZERO);
            map.put("mechanism", t.getTransactionType() != null ? t.getTransactionType() : "N/A");
            map.put("status", t.getStatus() != null ? t.getStatus() : "UNKNOWN");
            map.put("timestamp", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "N/A");
            
            String merchantName = "N/A";
            String customerName = "N/A";

            if (t.getInvoice() != null) {
                if (t.getInvoice().getMerchant() != null) {
                    merchantName = t.getInvoice().getMerchant().getBusinessName();
                    if (merchantName == null) merchantName = t.getInvoice().getMerchant().getOwnerName();
                }
                if (t.getInvoice().getCustomer() != null) {
                    customerName = t.getInvoice().getCustomer().getName();
                    if (customerName == null && t.getInvoice().getCustomer().getUser() != null) {
                        customerName = t.getInvoice().getCustomer().getUser().getEmail();
                    }
                }
            }
            
            map.put("merchantName", merchantName != null ? merchantName : "N/A");
            map.put("customerName", customerName != null ? customerName : "N/A");
            
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    public List<Map<String, Object>> getMerchants() {
        List<com.billme.merchant.MerchantProfile> merchants = merchantProfileRepository.findAllWithUser();
        return merchants.stream().map(m -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", m.getUser() != null ? m.getUser().getId() : 0);
            map.put("businessName", m.getBusinessName() != null ? m.getBusinessName() : "N/A");
            map.put("ownerName", m.getOwnerName() != null ? m.getOwnerName() : "N/A");
            map.put("email", m.getUser() != null ? m.getUser().getEmail() : "N/A");
            map.put("status", (m.getUser() != null && m.getUser().isActive()) ? "ACTIVE" : "INACTIVE");
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    public List<Map<String, Object>> getCustomers() {
        List<com.billme.customer.CustomerProfile> customers = customerProfileRepository.findAllWithUser();
        return customers.stream().map(c -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", c.getId() != null ? c.getId() : 0);
            map.put("name", c.getName() != null ? c.getName() : "N/A");
            map.put("email", c.getUser() != null ? c.getUser().getEmail() : "N/A");
            map.put("joined", c.getCreatedAt() != null ? c.getCreatedAt().toString() : "N/A");
            
            // Calculate LTV
            BigDecimal ltv = BigDecimal.ZERO;
            if (c.getUser() != null) {
                ltv = transactionRepository.sumCustomerSpending(c.getUser().getId());
            }
            map.put("ltv", ltv != null ? ltv : BigDecimal.ZERO);
            return map;
        }).collect(java.util.stream.Collectors.toList());
    }

    public List<Map<String, Object>> getRevenueBreakdown() {
        List<Transaction> all = transactionRepository.findAllWithInvoiceDetails();
        Map<java.time.LocalDate, BigDecimal> daily = new HashMap<>();
        
        for (Transaction t : all) {
            if (t.getStatus() == com.billme.transaction.TransactionStatus.SUCCESS && t.getCreatedAt() != null) {
                java.time.LocalDate date = t.getCreatedAt().toLocalDate();
                daily.put(date, daily.getOrDefault(date, BigDecimal.ZERO).add(t.getAmount()));
            }
        }

        return daily.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    Map<String, Object> m = new HashMap<>();
                    m.put("date", e.getKey().toString());
                    m.put("amount", e.getValue());
                    return m;
                }).collect(java.util.stream.Collectors.toList());
    }

    @org.springframework.transaction.annotation.Transactional
    public void updateMerchantStatus(Long id, boolean active) {
        userRepository.findById(id).ifPresent(u -> {
            u.setActive(active);
            userRepository.save(u);
        });
    }

    public List<Map<String, Object>> getFraudAlerts() {
        List<Transaction> transactions = transactionRepository.findAllWithInvoiceDetails();
        java.time.LocalDateTime oneHourAgo = java.time.LocalDateTime.now().minusHours(1);
        
        // Count transactions per customer in the last hour
        Map<Long, Long> customerTxnCount = transactions.stream()
                .filter(t -> t.getCreatedAt() != null && t.getCreatedAt().isAfter(oneHourAgo))
                .filter(t -> t.getInvoice() != null && t.getInvoice().getCustomer() != null && t.getInvoice().getCustomer().getUser() != null)
                .collect(java.util.stream.Collectors.groupingBy(
                        t -> t.getInvoice().getCustomer().getUser().getId(),
                        java.util.stream.Collectors.counting()
                ));

        return transactions.stream()
                .filter(t -> {
                    boolean isHighAmount = t.getAmount() != null && t.getAmount().compareTo(new BigDecimal("10000")) > 0;
                    boolean isFailed = "FAILED".equals(t.getStatus()) || (t.getStatus() != null && t.getStatus().toString().equals("FAILED"));
                    
                    Long customerId = null;
                    if (t.getInvoice() != null && t.getInvoice().getCustomer() != null && t.getInvoice().getCustomer().getUser() != null) {
                        customerId = t.getInvoice().getCustomer().getUser().getId();
                    }
                    boolean isHighFrequency = customerId != null && customerTxnCount.getOrDefault(customerId, 0L) > 3;

                    return isHighAmount || isFailed || isHighFrequency;
                })
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", t.getId());
                    map.put("amount", t.getAmount());
                    map.put("status", t.getStatus());
                    map.put("timestamp", t.getCreatedAt() != null ? t.getCreatedAt().toString() : "N/A");
                    
                    String merchantName = "N/A";
                    String customerName = "N/A";
                    if (t.getInvoice() != null) {
                        if (t.getInvoice().getMerchant() != null) {
                            merchantName = t.getInvoice().getMerchant().getBusinessName();
                            if (merchantName == null) merchantName = t.getInvoice().getMerchant().getOwnerName();
                        }
                        if (t.getInvoice().getCustomer() != null) customerName = t.getInvoice().getCustomer().getName();
                    }
                    map.put("merchant", merchantName != null ? merchantName : "N/A");
                    map.put("customer", customerName != null ? customerName : "N/A");
                    
                    // Reason for flagging
                    StringBuilder reason = new StringBuilder();
                    if (t.getAmount() != null && t.getAmount().compareTo(new BigDecimal("10000")) > 0) reason.append("High Amount; ");
                    if ("FAILED".equals(t.getStatus()) || (t.getStatus() != null && t.getStatus().toString().equals("FAILED"))) reason.append("Failed Status; ");
                    
                    Long customerId = null;
                    if (t.getInvoice() != null && t.getInvoice().getCustomer() != null && t.getInvoice().getCustomer().getUser() != null) {
                        customerId = t.getInvoice().getCustomer().getUser().getId();
                    }
                    if (customerId != null && customerTxnCount.getOrDefault(customerId, 0L) > 3) reason.append("High Frequency; ");
                    
                    map.put("reason", reason.toString().trim());
                    return map;
                })
                .sorted((a, b) -> {
                    String timeA = (String) a.get("timestamp");
                    String timeB = (String) b.get("timestamp");
                    return timeB.compareTo(timeA);
                })
                .limit(20)
                .collect(java.util.stream.Collectors.toList());
    }

    public Map<String, Object> getMerchantDetails(Long id) {
        com.billme.merchant.MerchantProfile m = merchantProfileRepository.findByUser_Id(id)
            .orElseThrow(() -> new RuntimeException("Merchant profile not found for user ID: " + id));

        Map<String, Object> details = new HashMap<>();
        details.put("id", id);
        details.put("businessName", m.getBusinessName() != null ? m.getBusinessName() : "N/A");
        details.put("ownerName", m.getOwnerName() != null ? m.getOwnerName() : "N/A");
        details.put("email", m.getUser() != null ? m.getUser().getEmail() : "N/A");
        details.put("phone", m.getPhone() != null ? m.getPhone() : "N/A");
        details.put("status", (m.getUser() != null && m.getUser().isActive()) ? "ACTIVE" : "SUSPENDED");
        details.put("address", m.getAddress() != null ? m.getAddress() : "N/A");
        details.put("city", m.getCity() != null ? m.getCity() : "N/A");
        details.put("state", m.getState() != null ? m.getState() : "N/A");
        details.put("pinCode", m.getPinCode() != null ? m.getPinCode() : "N/A");
        details.put("gstin", m.getGstin() != null ? m.getGstin() : "N/A");
        details.put("createdAt", m.getCreatedAt() != null ? m.getCreatedAt().toString() : "N/A");
        
        // Stats
        details.put("totalTransactions", transactionRepository.countMerchantTransactions(id));
        
        return details;
    }
}