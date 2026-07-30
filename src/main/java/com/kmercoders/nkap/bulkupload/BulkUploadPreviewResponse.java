package com.kmercoders.nkap.bulkupload;

import com.kmercoders.nkap.bulkupload.csv.ParsedTransactionRow;

import java.util.List;

public record BulkUploadPreviewResponse(
    Long accountId,
    List<ParsedTransactionRow> rows,
    List<BudgetToCreateDTO> budgetsToCreate,
    int totalRows
) {
}
