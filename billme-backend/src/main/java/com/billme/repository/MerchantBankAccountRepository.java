package com.billme.repository;

import com.billme.merchant.MerchantBankAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MerchantBankAccountRepository extends JpaRepository<MerchantBankAccount, Long> {
    List<MerchantBankAccount> findByMerchant_User_Id(Long userId);
    
    Optional<MerchantBankAccount> findByIdAndMerchant_User_Id(Long id, Long userId);
}
