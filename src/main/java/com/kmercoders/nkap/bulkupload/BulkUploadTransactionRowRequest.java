package com.kmercoders.nkap.bulkupload;

import com.kmercoders.nkap.transaction.Direction;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

public class BulkUploadTransactionRowRequest {

    @NotNull(message = "Transaction date is required")
    private LocalDate transactionDate;

    @NotNull(message = "Amount is required")
    @PositiveOrZero(message = "Amount must be zero or positive")
    private BigDecimal amount;

    @NotNull(message = "Direction is required")
    private Direction direction;

    @Size(max = 100, message = "Description must be 100 characters or fewer")
    private String description;

    public LocalDate getTransactionDate()                  { return transactionDate; }
    public void setTransactionDate(LocalDate transactionDate) { this.transactionDate = transactionDate; }
    public BigDecimal getAmount()                           { return amount; }
    public void setAmount(BigDecimal amount)                { this.amount = amount; }
    public Direction getDirection()                         { return direction; }
    public void setDirection(Direction direction)           { this.direction = direction; }
    public String getDescription()                          { return description; }
    public void setDescription(String description)          { this.description = description; }
}
