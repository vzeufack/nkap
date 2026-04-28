package com.kmercoders.nkap.budget;

import jakarta.persistence.*;
import java.time.Month;

import com.kmercoders.nkap.appuser.AppUser;

@Entity
@Table(
    name = "budgets",
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"app_user_id", "month", "year"})
    }
)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Month month;

    @Column(nullable = false)
    private int year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    // Constructors
    public Budget() {}

    public Budget(Month month, int year, AppUser appUser) {
        this.month = month;
        this.year = year;
        this.appUser = appUser;
    }

    // Getters & Setters
    public Long getId() { return id; }

    public Month getMonth() { return month; }
    public void setMonth(Month month) { this.month = month; }

    public int getYear() { return year; }
    public void setYear(int year) { this.year = year; }

    public AppUser getAppUser() { return appUser; }
    public void setAppUser(AppUser appUser) { this.appUser = appUser; }
}
