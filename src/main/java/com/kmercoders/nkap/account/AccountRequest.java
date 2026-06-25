package com.kmercoders.nkap.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class AccountRequest {

    @NotBlank(message = "Account name is required")
    @Size(max = 100, message = "Account name must be 100 characters or fewer")
    private String name;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @NotNull(message = "Balance is required")
    private BigDecimal balance;

    public String getName()                       { return name; }
    public void setName(String name)              { this.name = name; }
    public AccountType getAccountType()           { return accountType; }
    public void setAccountType(AccountType type)  { this.accountType = type; }
    public BigDecimal getBalance()                { return balance; }
    public void setBalance(BigDecimal balance)    { this.balance = balance; }
}
