package com.billme.repository;

import com.billme.transaction.LedgerEntry;
import com.billme.transaction.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, Long> {
    boolean existsByWalletIdAndReferenceIdAndType(Long walletId, String referenceId, LedgerEntryType type);

    @Query(value = "SELECT DATE(created_at) as bucket, COALESCE(SUM(amount), 0) as total FROM ledger_entries " +
                   "WHERE wallet_id = :walletId AND type = :type " +
                   "AND created_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findDailyTotals(@Param("walletId") Long walletId, @Param("type") String type,
                                   @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT YEARWEEK(created_at) as bucket, COALESCE(SUM(amount), 0) as total FROM ledger_entries " +
                   "WHERE wallet_id = :walletId AND type = :type " +
                   "AND created_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findWeeklyTotals(@Param("walletId") Long walletId, @Param("type") String type, 
                                  @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT DATE_FORMAT(created_at, '%Y-%m') as bucket, COALESCE(SUM(amount), 0) as total FROM ledger_entries " +
                   "WHERE wallet_id = :walletId AND type = :type " +
                   "AND created_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findMonthlyTotals(@Param("walletId") Long walletId, @Param("type") String type, 
                                   @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    @Query(value = "SELECT YEAR(created_at) as bucket, COALESCE(SUM(amount), 0) as total FROM ledger_entries " +
                   "WHERE wallet_id = :walletId AND type = :type " +
                   "AND created_at BETWEEN :start AND :end GROUP BY bucket ORDER BY bucket ASC", nativeQuery = true)
    List<Object[]> findYearlyTotals(@Param("walletId") Long walletId, @Param("type") String type, 
                                  @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);
}
