package com.kmercoders.nkap.category;

import java.math.BigDecimal;

public class AutoAllocateResponse {

    private final BigDecimal totalAllocated;
    private final int transfersCreated;
    private final BigDecimal remainingUnallocated;

    public AutoAllocateResponse(BigDecimal totalAllocated, int transfersCreated, BigDecimal remainingUnallocated) {
        this.totalAllocated       = totalAllocated;
        this.transfersCreated     = transfersCreated;
        this.remainingUnallocated = remainingUnallocated;
    }

    public BigDecimal getTotalAllocated()       { return totalAllocated; }
    public int getTransfersCreated()            { return transfersCreated; }
    public BigDecimal getRemainingUnallocated() { return remainingUnallocated; }
}
