package com.kmercoders.nkap.account;

import java.math.BigDecimal;

import com.kmercoders.nkap.financialinstitution.FinancialInstitutionDTO;

public class AccountDTO {

    private final Long id;
    private final String name;
    private final AccountType accountType;
    private final BigDecimal balance;
    private final FinancialInstitutionDTO financialInstitution;

    public AccountDTO(Long id, String name, AccountType accountType, BigDecimal balance,
                       FinancialInstitutionDTO financialInstitution) {
        this.id                    = id;
        this.name                  = name;
        this.accountType           = accountType;
        this.balance               = balance;
        this.financialInstitution  = financialInstitution;
    }

    public static AccountDTO from(Account account) {
        return new AccountDTO(
            account.getId(),
            account.getName(),
            account.getType(),
            account.getBalance(),
            account.getFinancialInstitution() == null
                ? null
                : FinancialInstitutionDTO.from(account.getFinancialInstitution())
        );
    }

    public Long getId()                                    { return id; }
    public String getName()                                { return name; }
    public AccountType getAccountType()                    { return accountType; }
    public BigDecimal getBalance()                         { return balance; }
    public FinancialInstitutionDTO getFinancialInstitution() { return financialInstitution; }
}
