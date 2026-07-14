package com.kmercoders.nkap.transaction;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountId(Long accountId);
    List<Transaction> findByBudgetId(Long budgetId);
    List<Transaction> findByBudgetIdOrderByTransactionDateDesc(Long budgetId);
    Optional<Transaction> findByIdAndBudgetAppUserId(Long id, Long appUserId);
    boolean existsByBudgetCategoryId(Long budgetCategoryId);
}
