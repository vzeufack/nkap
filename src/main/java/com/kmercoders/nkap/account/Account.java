package com.kmercoders.nkap.account;

import java.io.Serializable;
import java.math.BigDecimal;

import com.kmercoders.nkap.appuser.AppUser;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "accounts")
public class Account implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    @Column(nullable = false, updatable = false)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private BigDecimal balance;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    public Account() {
    }

    public Account(AccountType accountType, String name, BigDecimal balance, AppUser appUser) {
        this.accountType = accountType;
        this.name = name;
        this.balance = balance;
        this.appUser = appUser;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public AccountType getType() { return accountType; }
    public void setType(AccountType accountType) { this.accountType = accountType; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }

    public AppUser getAppUser() { return appUser; }
    public void setAppUser(AppUser appUser) { this.appUser = appUser; }
}