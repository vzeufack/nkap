package com.kmercoders.nkap.bulkupload.csv;

import com.kmercoders.nkap.transaction.Direction;

import java.math.BigDecimal;
import java.time.LocalDate;

public record ParsedTransactionRow(
    int lineNumber,
    LocalDate transactionDate,
    BigDecimal amount,
    Direction direction,
    String description
) {
}
