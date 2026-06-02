package com.kmercoders.nkap.category;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public class CategoryRequest {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be 100 characters or fewer")
    private String name;

    @NotNull(message = "Allocation is required")
    @DecimalMin(value = "0.00", message = "Allocation must be zero or greater")
    private BigDecimal allocation;

    @DecimalMin(value = "0.00", message = "Balance must be zero or greater")
    private BigDecimal balance;

    public String getName()                 { return name; }
    public void setName(String name)        { this.name = name; }
    public BigDecimal getAllocation()        { return allocation; }
    public void setAllocation(BigDecimal a) { this.allocation = a; }
    public BigDecimal getBalance()          { return balance; }
    public void setBalance(BigDecimal b)    { this.balance = b; }
}