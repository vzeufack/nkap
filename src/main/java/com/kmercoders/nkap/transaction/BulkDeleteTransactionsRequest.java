package com.kmercoders.nkap.transaction;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public class BulkDeleteTransactionsRequest {

    @NotEmpty(message = "Select at least one transaction")
    @Size(max = 500, message = "Cannot delete more than 500 transactions at once")
    private List<@NotNull Long> ids;

    public List<Long> getIds()           { return ids; }
    public void setIds(List<Long> ids)   { this.ids = ids; }
}
