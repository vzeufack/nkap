package com.kmercoders.nkap.bulkupload;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public class BulkUploadConfirmRequest {

    @NotNull(message = "Account is required")
    private Long accountId;

    @NotEmpty(message = "At least one transaction is required")
    @Valid
    private List<BulkUploadTransactionRowRequest> transactions;

    public Long getAccountId()                                              { return accountId; }
    public void setAccountId(Long accountId)                                { this.accountId = accountId; }
    public List<BulkUploadTransactionRowRequest> getTransactions()          { return transactions; }
    public void setTransactions(List<BulkUploadTransactionRowRequest> transactions) { this.transactions = transactions; }
}
