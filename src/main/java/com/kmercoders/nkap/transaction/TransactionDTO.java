package com.kmercoders.nkap.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionDTO {

    private Long id;
    private BigDecimal amount;
    private LocalDate transactionDate;
    private TransactionType transactionType;
    private String description;
    private String note;
    private Long accountId;
    private Long categoryId;
    private Long budgetId;

    private TransactionDTO() {}

    public static TransactionDTO from(Transaction t) {
        TransactionDTO dto = new TransactionDTO();
        dto.id              = t.getId();
        dto.amount          = t.getAmount();
        dto.transactionDate = t.getTransactionDate();
        dto.transactionType = t.getTransactionType();
        dto.description     = t.getDescription();
        dto.note            = t.getNote();
        dto.accountId       = t.getAccount()         != null ? t.getAccount().getId()                          : null;
        dto.categoryId      = t.getBudgetCategory()  != null ? t.getBudgetCategory().getCategory().getId()     : null;
        dto.budgetId        = t.getBudget()          != null ? t.getBudget().getId()                           : null;
        return dto;
    }

    public Long getId()                        { return id; }
    public BigDecimal getAmount()              { return amount; }
    public LocalDate getTransactionDate()      { return transactionDate; }
    public TransactionType getTransactionType(){ return transactionType; }
    public String getDescription()             { return description; }
    public String getNote()                    { return note; }
    public Long getAccountId()                 { return accountId; }
    public Long getCategoryId()                { return categoryId; }
    public Long getBudgetId()                  { return budgetId; }
}
