package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.AccountType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;

public class TransactionListItemDTO {

    private Long id;
    private LocalDate transactionDate;
    private BigDecimal amount;
    private BigDecimal signedAmount;
    private Direction direction;
    private TransactionType transactionType;
    private String description;
    private String note;
    private String groupName;
    private String categoryName;
    private String accountName;
    private AccountType accountType;
    private Month budgetMonth;
    private int budgetYear;

    private TransactionListItemDTO() {}

    public static TransactionListItemDTO from(Transaction t) {
        TransactionListItemDTO dto = new TransactionListItemDTO();
        dto.id              = t.getId();
        dto.transactionDate = t.getTransactionDate();
        dto.amount          = t.getAmount();
        dto.signedAmount    = t.getDirection() == Direction.DEBIT ? t.getAmount().negate() : t.getAmount();
        dto.direction       = t.getDirection();
        dto.transactionType = t.getTransactionType();
        dto.description     = t.getDescription();
        dto.note            = t.getNote();
        dto.groupName       = t.getBudgetCategory() != null ? t.getBudgetCategory().getCategory().getGroup().getName() : null;
        dto.categoryName    = t.getBudgetCategory() != null ? t.getBudgetCategory().getCategory().getName()            : null;
        dto.accountName     = t.getAccount()        != null ? t.getAccount().getName()                                 : null;
        dto.accountType     = t.getAccount()        != null ? t.getAccount().getType()                                 : null;
        dto.budgetMonth     = t.getBudget().getMonth();
        dto.budgetYear      = t.getBudget().getYear();
        return dto;
    }

    public Long getId()                        { return id; }
    public LocalDate getTransactionDate()      { return transactionDate; }
    public BigDecimal getAmount()              { return amount; }
    public BigDecimal getSignedAmount()        { return signedAmount; }
    public Direction getDirection()            { return direction; }
    public TransactionType getTransactionType(){ return transactionType; }
    public String getDescription()             { return description; }
    public String getNote()                    { return note; }
    public String getGroupName()               { return groupName; }
    public String getCategoryName()            { return categoryName; }
    public String getAccountName()             { return accountName; }
    public AccountType getAccountType()        { return accountType; }
    public Month getBudgetMonth()              { return budgetMonth; }
    public int getBudgetYear()                 { return budgetYear; }

    /** Transaction type as a display label, e.g. STANDARD → "Standard". */
    public String getTransactionTypeLabel() {
        return toLabel(transactionType);
    }

    /** Account type as a display label, e.g. CHECKING → "Checking". */
    public String getAccountTypeLabel() {
        return toLabel(accountType);
    }

    private static String toLabel(Enum<?> value) {
        if (value == null) {
            return null;
        }
        String name = value.name();
        return name.charAt(0) + name.substring(1).toLowerCase();
    }
}
