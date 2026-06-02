package com.kmercoders.nkap.category;

import java.math.BigDecimal;

public class CategoryDTO {

    private final Long id;
    private final String name;
    private final BigDecimal balance;
    private final BigDecimal allocation;
    private final Long groupId;

    public CategoryDTO(Long id, String name, BigDecimal balance,
                       BigDecimal allocation, Long groupId) {
        this.id         = id;
        this.name       = name;
        this.balance    = balance;
        this.allocation = allocation;
        this.groupId    = groupId;
    }

    public static CategoryDTO from(BudgetCategory bc) {
        Category c = bc.getCategory();
        return new CategoryDTO(
            c.getId(),
            c.getName(),
            c.getBalance(),
            bc.getAllocation(),
            c.getGroup().getId()
        );
    }

    public Long getId()               { return id; }
    public String getName()           { return name; }
    public BigDecimal getBalance()    { return balance; }
    public BigDecimal getAllocation()  { return allocation; }
    public Long getGroupId()          { return groupId; }
}