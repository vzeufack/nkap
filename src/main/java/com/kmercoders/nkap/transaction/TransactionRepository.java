package com.kmercoders.nkap.transaction;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, Long>, JpaSpecificationExecutor<Transaction> {
    List<Transaction> findByAccountId(Long accountId);
    List<Transaction> findByBudgetId(Long budgetId);
    List<Transaction> findByBudgetIdAndTransactionType(Long budgetId, TransactionType transactionType);
    List<Transaction> findByBudgetIdOrderByTransactionDateDesc(Long budgetId);
    Optional<Transaction> findByIdAndBudgetAppUserId(Long id, Long appUserId);
    List<Transaction> findByTransferId(UUID transferId);

    /** Same as the inherited method, but loads the associations the transactions table displays in one query. */
    @Override
    @EntityGraph(attributePaths = {"account", "budgetCategory.category.group", "budget"})
    Page<Transaction> findAll(Specification<Transaction> spec, Pageable pageable);
    boolean existsByBudgetCategoryId(Long budgetCategoryId);
    boolean existsByAccountId(Long accountId);

    @Query("select distinct t.account.id from Transaction t where t.account is not null and t.account.appUser.id = :appUserId")
    Set<Long> findAccountIdsWithTransactionsByAppUserId(@Param("appUserId") Long appUserId);
}
