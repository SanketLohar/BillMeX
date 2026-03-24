package com.billme.transaction;

import com.billme.repository.LedgerEntryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    @Transactional
    public void record(Long walletId, BigDecimal amount, LedgerEntryType type, BigDecimal balanceAfter, String referenceId) {
        LedgerEntry entry = LedgerEntry.builder()
                .walletId(walletId)
                .amount(amount)
                .type(type)
                .balanceAfter(balanceAfter)
                .referenceId(referenceId)
                .build();
        ledgerEntryRepository.save(entry);
    }
}
