package com.kmercoders.nkap.category;

import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.transaction.Transaction;
import jakarta.persistence.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(
    name = "budget_category",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"budget_id", "category_id"})
    }
)
public class BudgetCategory implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id", nullable = false)
    private Budget budget;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal allocation = BigDecimal.ZERO;

    @OneToMany(mappedBy = "budgetCategory", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Transaction> transactions = new ArrayList<>();

    protected BudgetCategory() {}

    public BudgetCategory(Budget budget, Category category, BigDecimal allocation) {
        this.budget     = budget;
        this.category   = category;
        this.allocation = allocation;
    }

    public Long getId()                              { return id; }
    public Budget getBudget()                        { return budget; }
    public Category getCategory()                    { return category; }
    public BigDecimal getAllocation()                 { return allocation; }
    public void setAllocation(BigDecimal allocation) { this.allocation = allocation; }
}
