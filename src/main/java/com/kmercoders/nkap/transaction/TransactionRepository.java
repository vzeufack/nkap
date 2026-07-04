package com.kmercoders.nkap.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountId(Long accountId);
    List<Transaction> findByCategoryId(Long categoryId);
    List<Transaction> findByBudgetId(Long budgetId);
    List<Transaction> findByBudgetIdOrderByTransactionDateDesc(Long budgetId);
}
