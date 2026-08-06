package com.kmercoders.nkap.category;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public class CategoryTransferRequest {

    @NotNull(message = "Source category is required")
    private Long sourceCategoryId;

    @NotNull(message = "Target category is required")
    private Long targetCategoryId;

    @NotNull(message = "Amount is required")
    @Positive(message = "Amount must be greater than zero")
    private BigDecimal amount;

    public Long getSourceCategoryId()                    { return sourceCategoryId; }
    public void setSourceCategoryId(Long sourceCategoryId) { this.sourceCategoryId = sourceCategoryId; }
    public Long getTargetCategoryId()                    { return targetCategoryId; }
    public void setTargetCategoryId(Long targetCategoryId) { this.targetCategoryId = targetCategoryId; }
    public BigDecimal getAmount()                         { return amount; }
    public void setAmount(BigDecimal amount)              { this.amount = amount; }
}
