package com.billme.transaction;

import com.billme.repository.TransactionRepository;
import com.billme.repository.UserRepository;
import com.billme.transaction.dto.TransactionHistoryResponse;
import com.billme.user.User;
import com.billme.wallet.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final UserRepository userRepository;

    public Page<TransactionHistoryResponse> getUserTransactions(
            Integer page,
            Integer size,
            TransactionType type,
            TransactionStatus status,
            LocalDate from,
            LocalDate to
    ) {

        User loggedUser = getLoggedInUser();
        log.info("Fetching transactions for user: {}", loggedUser.getEmail());

        Pageable pageable = PageRequest.of(
                page,
                size,
                Sort.by(Sort.Direction.DESC, "createdAt")
        );

        LocalDateTime fromDate = (from != null)
                ? from.atStartOfDay()
                : null;

        LocalDateTime toDate = (to != null)
                ? to.atTime(LocalTime.MAX)
                : null;

        Page<Transaction> transactions = transactionRepository
                .findUserTransactions(
                        loggedUser.getId(),
                        type,
                        status,
                        fromDate,
                        toDate,
                        pageable
                );

        return transactions.map(transaction ->
                mapToHistoryResponse(transaction, loggedUser)
        );
    }

    private TransactionHistoryResponse mapToHistoryResponse(
            Transaction transaction,
            User loggedUser
    ) {

        Wallet sender = transaction.getSenderWallet();
        Wallet receiver = transaction.getReceiverWallet();

        Direction direction;
        String counterparty;

        if (sender != null &&
                sender.getUser().getId().equals(loggedUser.getId())) {

            direction = Direction.DEBIT;

            counterparty = (receiver != null)
                    ? receiver.getUser().getEmail()
                    : "External";

        } else {

            direction = Direction.CREDIT;

            counterparty = (sender != null)
                    ? sender.getUser().getEmail()
                    : "External";
        }

        return TransactionHistoryResponse.builder()
                .transactionId(transaction.getId())
                .direction(direction)
                .amount(transaction.getAmount())
                .type(transaction.getTransactionType())
                .status(transaction.getStatus())
                .counterparty(counterparty)
                .timestamp(transaction.getCreatedAt())
                .build();
    }

    private User getLoggedInUser() {

        String email = SecurityContextHolder
                .getContext()
                .getAuthentication()
                .getName();

        return userRepository.findByEmail(email)
                .orElseThrow(() ->
                        new RuntimeException("Authenticated user not found")
                );
    }


}