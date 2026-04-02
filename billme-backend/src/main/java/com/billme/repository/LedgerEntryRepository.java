package com.billme.repository;

import com.billme.transaction.LedgerEntry;
import com.billme.transaction.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByWalletIdAndReferenceIdAndType(Long walletId, String referenceId, LedgerEntryType type);
}
