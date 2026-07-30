package com.kmercoders.nkap.bulkupload.csv;

import java.io.Reader;
import java.util.List;

public interface BulkUploadCsvParser {

    /**
     * Parses the given CSV content into transaction rows.
     * @throws CsvValidationException if the header doesn't match the expected format,
     *         or any row fails to parse. Never returns a partial result.
     */
    List<ParsedTransactionRow> parse(Reader reader);
}
