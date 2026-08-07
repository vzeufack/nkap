package com.kmercoders.nkap.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionSummaryDTO {

    private Long id;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private Direction direction;
    private TransactionType transactionType;
    private String description;
    private String note;
    private String categoryName;
    private String accountName;
    private Long   categoryId;
    private Long   accountId;
    private Long   budgetId;

    private TransactionSummaryDTO() {}

    public static TransactionSummaryDTO from(Transaction t) {
        TransactionSummaryDTO dto = new TransactionSummaryDTO();
        dto.id              = t.getId();
        dto.transactionDate = t.getTransactionDate();
        dto.amount          = t.getAmount();
        dto.direction       = t.getDirection();
        dto.transactionType = t.getTransactionType();
        dto.description     = t.getDescription();
        dto.note            = t.getNote();
        dto.categoryName    = t.getBudgetCategory() != null ? t.getBudgetCategory().getCategory().getName() : null;
        dto.accountName     = t.getAccount()  != null ? t.getAccount().getName()  : null;
        dto.categoryId      = t.getBudgetCategory() != null ? t.getBudgetCategory().getCategory().getId() : null;
        dto.accountId       = t.getAccount()  != null ? t.getAccount().getId()  : null;
        dto.budgetId        = t.getBudget().getId();
        return dto;
    }

    public Long getId()                        { return id; }
    public LocalDate getTransactionDate()      { return transactionDate; }
    public BigDecimal getAmount()              { return amount; }
    public Direction getDirection()            { return direction; }
    public TransactionType getTransactionType(){ return transactionType; }
    public String getDescription()             { return description; }
    public String getNote()                    { return note; }
    public String getCategoryName()            { return categoryName; }
    public String getAccountName()             { return accountName; }
    public Long getCategoryId()                { return categoryId; }
    public Long getAccountId()                 { return accountId; }
    public Long getBudgetId()                  { return budgetId; }

    /**
     * Whether this transaction should be treated as "categorized" for display purposes.
     * A transaction linked to a category counts, but so do system-generated ADJUSTMENT
     * and TRANSFER transactions (account/category balance adjustments, category-to-category
     * transfers) — these never carry a user-assigned category, yet don't need the user's
     * attention the way a regular uncategorized transaction does.
     */
    public boolean isCategorized() {
        return categoryId != null
            || transactionType == TransactionType.ADJUSTMENT
            || transactionType == TransactionType.TRANSFER;
    }
}
