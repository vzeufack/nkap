// BudgetCategoryRepository.java
package com.kmercoders.nkap.category;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface BudgetCategoryRepository
        extends JpaRepository<BudgetCategory, Long> {

    @Query("""
        SELECT bc FROM BudgetCategory bc
        LEFT JOIN FETCH bc.category c
        LEFT JOIN FETCH c.group
        WHERE bc.budget.id = :budgetId
    """)
    List<BudgetCategory> findByBudgetId(@Param("budgetId") Long budgetId);

    Optional<BudgetCategory> findByBudgetIdAndCategoryId(Long budgetId, Long categoryId);

    Optional<BudgetCategory> findByBudgetIdAndCategoryIdAndCategoryGroupId(
        Long budgetId, Long categoryId, Long groupId);

    boolean existsByCategoryId(Long categoryId);
}
