package com.kmercoders.nkap.category;

import com.kmercoders.nkap.group.Group;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "category")
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance = BigDecimal.ZERO;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<BudgetCategory> budgetCategories = new HashSet<>();

    protected Category() {}

    public Category(String name, Group group) {
        this.name = name;
        this.group = group;
    }

    // Getters & setters
    public Long getId()                             { return id; }
    public String getName()                         { return name; }
    public void setName(String name)                { this.name = name; }
    public BigDecimal getBalance()                  { return balance; }
    public void setBalance(BigDecimal balance)      { this.balance = balance; }
    public Group getGroup()                         { return group; }
    public void setGroup(Group group)               { this.group = group; }
    public Set<BudgetCategory> getBudgetCategories(){ return budgetCategories; }
}