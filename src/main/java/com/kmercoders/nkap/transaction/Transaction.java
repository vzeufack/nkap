package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.category.Category;
import jakarta.persistence.*;
import jakarta.validation.constraints.PositiveOrZero;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDate;

@Entity
@Table(name = "transactions")
public class Transaction implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @PositiveOrZero
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private LocalDate transactionDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType transactionType;

    @Column(length = 500)
    private String note;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "budget_id")
    private Budget budget;

    protected Transaction() {}

    public Transaction(BigDecimal amount, LocalDate transactionDate, TransactionType transactionType,
                       String note, Account account, Category category, Budget budget) {
        this.amount = amount;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.note = note;
        this.account = account;
        this.category = category;
        this.budget = budget;
    }

    public Long getId() { return id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public LocalDate getTransactionDate() { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }

    public TransactionType getTransactionType() { return transactionType; }
    public void setTransactionType(TransactionType transactionType) { this.transactionType = transactionType; }

    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }

    public Account getAccount() { return account; }
    public void setAccount(Account account) { this.account = account; }

    public Category getCategory() { return category; }
    public void setCategory(Category category) { this.category = category; }

    public Budget getBudget() { return budget; }
    public void setBudget(Budget budget) { this.budget = budget; }
}
