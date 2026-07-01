package com.kmercoders.nkap.budget;

import jakarta.persistence.*;
import java.time.Month;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.group.Group;

@Entity
@Table(
    uniqueConstraints = {
        @UniqueConstraint(columnNames = {"app_user_id", "budget_month", "budget_year"})
    }
)
public class Budget {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "budget_month", nullable = false)
    private Month month;

    @Column(name = "budget_year", nullable = false)
    private int year;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "app_user_id", nullable = false)
    private AppUser appUser;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "budget_group_mapping",
        joinColumns = @JoinColumn(name = "budget_id"),
        inverseJoinColumns = @JoinColumn(name = "group_id")
    )
    @OrderBy("isDefault DESC, LOWER(name) ASC")
    private Set<Group> groups = new LinkedHashSet<>();

    @OneToMany(mappedBy = "budget", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BudgetCategory> budgetCategories = new HashSet<>();

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

    public Set<Group> getGroups() { return groups; }
    public void setGroups(Set<Group> groups) { this.groups = groups; }

    public void addGroup(Group group) {
        this.groups.add(group);
        group.getBudgets().add(this);
    }

    public void removeGroup(Group group) {
        this.groups.remove(group);
        group.getBudgets().remove(this);
    }

    public Set<BudgetCategory> getBudgetCategories() { return budgetCategories; }
}
