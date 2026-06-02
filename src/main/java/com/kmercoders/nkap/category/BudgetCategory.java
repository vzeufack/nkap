package com.kmercoders.nkap.category;

import com.kmercoders.nkap.budget.Budget;
import jakarta.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Objects;

@Entity
@Table(name = "budget_category")
public class BudgetCategory {

    @EmbeddedId
    private BudgetCategoryId id = new BudgetCategoryId();

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("budgetId")
    @JoinColumn(name = "budget_id")
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("categoryId")
    @JoinColumn(name = "category_id")
    private Category category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal allocation = BigDecimal.ZERO;

    protected BudgetCategory() {}

    public BudgetCategory(Budget budget, Category category, BigDecimal allocation) {
        this.budget     = budget;
        this.category   = category;
        this.allocation = allocation;
        this.id         = new BudgetCategoryId(budget.getId(), category.getId());
    }

    public BudgetCategoryId getId()                        { return id; }
    public Budget getBudget()                              { return budget; }
    public Category getCategory()                          { return category; }
    public BigDecimal getAllocation()                       { return allocation; }
    public void setAllocation(BigDecimal allocation)       { this.allocation = allocation; }

    // ── Composite key ──────────────────────────────────────────
    @Embeddable
    public static class BudgetCategoryId implements Serializable {

        @Column(name = "budget_id")
        private Long budgetId;

        @Column(name = "category_id")
        private Long categoryId;

        public BudgetCategoryId() {}

        public BudgetCategoryId(Long budgetId, Long categoryId) {
            this.budgetId   = budgetId;
            this.categoryId = categoryId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof BudgetCategoryId that)) return false;
            return Objects.equals(budgetId, that.budgetId)
                && Objects.equals(categoryId, that.categoryId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(budgetId, categoryId);
        }

        public Long getBudgetId()   { return budgetId; }
        public Long getCategoryId() { return categoryId; }
    }
}