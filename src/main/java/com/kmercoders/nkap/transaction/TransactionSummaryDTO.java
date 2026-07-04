package com.kmercoders.nkap.transaction;

import java.math.BigDecimal;
import java.time.LocalDate;

public class TransactionSummaryDTO {

    private Long id;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private TransactionType transactionType;
    private String description;
    private String note;
    private String categoryName;
    private String accountName;

    private TransactionSummaryDTO() {}

    public static TransactionSummaryDTO from(Transaction t) {
        TransactionSummaryDTO dto = new TransactionSummaryDTO();
        dto.id              = t.getId();
        dto.transactionDate = t.getTransactionDate();
        dto.amount          = t.getAmount();
        dto.transactionType = t.getTransactionType();
        dto.description     = t.getDescription();
        dto.note            = t.getNote();
        dto.categoryName    = t.getCategory() != null ? t.getCategory().getName() : null;
        dto.accountName     = t.getAccount()  != null ? t.getAccount().getName()  : null;
        return dto;
    }

    public Long getId()                        { return id; }
    public LocalDate getTransactionDate()      { return transactionDate; }
    public BigDecimal getAmount()              { return amount; }
    public TransactionType getTransactionType(){ return transactionType; }
    public String getDescription()             { return description; }
    public String getNote()                    { return note; }
    public String getCategoryName()            { return categoryName; }
    public String getAccountName()             { return accountName; }
}
