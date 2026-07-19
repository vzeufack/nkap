package com.kmercoders.nkap.transaction;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {
    List<Transaction> findByAccountId(Long accountId);
    List<Transaction> findByBudgetId(Long budgetId);
    List<Transaction> findByBudgetIdOrderByTransactionDateDesc(Long budgetId);
    Optional<Transaction> findByIdAndBudgetAppUserId(Long id, Long appUserId);
    boolean existsByBudgetCategoryId(Long budgetCategoryId);
    boolean existsByAccountId(Long accountId);

    @Query("select distinct t.account.id from Transaction t where t.account is not null and t.account.appUser.id = :appUserId")
    Set<Long> findAccountIdsWithTransactionsByAppUserId(@Param("appUserId") Long appUserId);
}
