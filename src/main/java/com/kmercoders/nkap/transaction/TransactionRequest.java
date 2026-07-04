package com.kmercoders.nkap.transaction;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionRequest {

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Amount must be zero or positive")
    private BigDecimal amount;

    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    @NotNull(message = "Transaction type is required")
    private TransactionType transactionType;

    @Size(max = 100, message = "Description must be 100 characters or fewer")
    private String description;

    @Size(max = 500, message = "Note must be 500 characters or fewer")
    private String note;

    private Long accountId;

    private Long categoryId;
    private Long budgetId;

    public BigDecimal getAmount()                        { return amount; }
    public void setAmount(BigDecimal amount)             { this.amount = amount; }
    public LocalDate getTransactionDate()                { return transactionDate; }
    public void setTransactionDate(LocalDate date)       { this.transactionDate = date; }
    public TransactionType getTransactionType()          { return transactionType; }
    public void setTransactionType(TransactionType type) { this.transactionType = type; }
    public String getDescription()                        { return description; }
    public void setDescription(String description)        { this.description = description; }
    public String getNote()                              { return note; }
    public void setNote(String note)                     { this.note = note; }
    public Long getAccountId()                           { return accountId; }
    public void setAccountId(Long accountId)             { this.accountId = accountId; }
    public Long getCategoryId()                          { return categoryId; }
    public void setCategoryId(Long categoryId)           { this.categoryId = categoryId; }
    public Long getBudgetId()                            { return budgetId; }
    public void setBudgetId(Long budgetId)               { this.budgetId = budgetId; }
}
