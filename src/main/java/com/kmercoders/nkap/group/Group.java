package com.kmercoders.nkap.group;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.category.Category;

@Entity
@Table(name = "budget_group")
public class Group {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private boolean isDefault = false;

    @JsonIgnore
    @ManyToMany(mappedBy = "groups", fetch = FetchType.LAZY)
    private Set<Budget> budgets = new LinkedHashSet<>();

    @OneToMany(mappedBy = "group", fetch = FetchType.LAZY)
    @OrderBy("name ASC")
    private List<Category> categories = new ArrayList<>();

    // Constructors
    public Group() {}

    public Group(String name) {
        this.name = name;
    }

    // Getters & Setters
    public Long getId() { return id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Boolean isDefault(){return isDefault;}
    public void setDefault(boolean isDefault){this.isDefault = isDefault; }

    public Set<Budget> getBudgets() { return budgets; }
    public void setBudgets(Set<Budget> budgets) { this.budgets = budgets; }

    public List<Category> getCategories() { return categories; }
}