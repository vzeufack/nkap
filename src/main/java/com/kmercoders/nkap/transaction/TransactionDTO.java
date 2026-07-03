package com.kmercoders.nkap.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionDTO {

    private final Long id;
    private final BigDecimal amount;
    private final LocalDate transactionDate;
    private final TransactionType transactionType;
    private final String note;
    private final Long accountId;
    private final Long categoryId;
    private final Long budgetId;

    public TransactionDTO(Long id, BigDecimal amount, LocalDate transactionDate,
                          TransactionType transactionType, String note,
                          Long accountId, Long categoryId, Long budgetId) {
        this.id              = id;
        this.amount          = amount;
        this.transactionDate = transactionDate;
        this.transactionType = transactionType;
        this.note            = note;
        this.accountId       = accountId;
        this.categoryId      = categoryId;
        this.budgetId        = budgetId;
    }

    public static TransactionDTO from(Transaction t) {
        return new TransactionDTO(
            t.getId(),
            t.getAmount(),
            t.getTransactionDate(),
            t.getTransactionType(),
            t.getNote(),
            t.getAccount()   != null ? t.getAccount().getId()   : null,
            t.getCategory() != null ? t.getCategory().getId() : null,
            t.getBudget()   != null ? t.getBudget().getId()   : null
        );
    }

    public Long getId()                        { return id; }
    public BigDecimal getAmount()              { return amount; }
    public LocalDate getTransactionDate()      { return transactionDate; }
    public TransactionType getTransactionType(){ return transactionType; }
    public String getNote()                    { return note; }
    public Long getAccountId()                 { return accountId; }
    public Long getCategoryId()                { return categoryId; }
    public Long getBudgetId()                  { return budgetId; }
}
