package com.kmercoders.nkap.account;

import java.math.BigDecimal;

public class AccountDTO {

    private final Long id;
    private final String name;
    private final AccountType accountType;
    private final BigDecimal balance;

    public AccountDTO(Long id, String name, AccountType accountType, BigDecimal balance) {
        this.id          = id;
        this.name        = name;
        this.accountType = accountType;
        this.balance     = balance;
    }

    public static AccountDTO from(Account account) {
        return new AccountDTO(
            account.getId(),
            account.getName(),
            account.getType(),
            account.getBalance()
        );
    }

    public Long getId()                  { return id; }
    public String getName()              { return name; }
    public AccountType getAccountType()  { return accountType; }
    public BigDecimal getBalance()       { return balance; }
}
