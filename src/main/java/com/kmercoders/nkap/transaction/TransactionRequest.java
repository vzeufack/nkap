package com.kmercoders.nkap.transaction;

import jakarta.validation.constraints.NotNull;

public class TransactionRequest extends TransactionDetailsRequest {

    @NotNull(message = "Budget is required")
    private Long budgetId;

    public Long getBudgetId()                            { return budgetId; }
    public void setBudgetId(Long budgetId)               { this.budgetId = budgetId; }
}
