package com.billme.customer;

import com.billme.customer.dto.CustomerLookupResponse;
import com.billme.repository.CustomerProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerProfileRepository customerProfileRepository;

    public CustomerLookupResponse findCustomerByEmail(String email) {

        CustomerProfile profile = customerProfileRepository
                .findByUser_Email(email)
                .orElseThrow(() -> new RuntimeException("Customer not found"));

        return CustomerLookupResponse.builder()
                .id(profile.getId())
                .name(profile.getName())
                .email(profile.getUser().getEmail())
                .state(profile.getState())
                .city(profile.getCity())
                .build();
    }
}