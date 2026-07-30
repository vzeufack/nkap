package com.kmercoders.nkap.bulkupload.csv;

import com.kmercoders.nkap.transaction.Direction;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Bank of America Checking/Savings export: a summary preamble precedes the real
 * "Date,Description,Amount,Running Bal." header, and the first data row after it
 * is always the non-transaction "Beginning balance as of ..." row.
 */
@Component
public class BofaCheckingSavingsCsvParser implements BulkUploadCsvParser {

    private static final String EXPECTED_HEADER = "Date,Description,Amount,Running Bal.";
    private static final int MAX_PREAMBLE_LINES_TO_SCAN = 20;

    @Override
    public List<ParsedTransactionRow> parse(Reader reader) {
        List<String> lines = CsvParsingUtils.readLines(reader);

        int headerLineIndex = -1;
        for (int i = 0; i < Math.min(lines.size(), MAX_PREAMBLE_LINES_TO_SCAN); i++) {
            if (lines.get(i).trim().equals(EXPECTED_HEADER)) {
                headerLineIndex = i;
                break;
            }
        }
        if (headerLineIndex == -1) {
            throw new CsvValidationException(List.of(
                "Unrecognized file format. Expected columns: " + EXPECTED_HEADER));
        }

        String csvBody = CsvParsingUtils.buildCsvBody(lines.subList(headerLineIndex, lines.size()));
        List<String> errors = new ArrayList<>();
        List<ParsedTransactionRow> rows = new ArrayList<>();

        try (CSVParser parser = CsvParsingUtils.CSV_FORMAT.parse(new StringReader(csvBody))) {
            for (CSVRecord csvRecord : parser) {
                int fileLine = headerLineIndex + 1 + (int) csvRecord.getRecordNumber();
                parseRecord(csvRecord, fileLine, errors, rows);
            }
        } catch (UncheckedIOException e) {
            throw new CsvValidationException(List.of(
                "Unable to parse the CSV file. Check the quoting on fields containing commas or quote characters."));
        } catch (IOException e) {
            throw new CsvValidationException(List.of("Unable to read the uploaded file."));
        }

        if (!errors.isEmpty()) {
            throw new CsvValidationException(errors);
        }
        if (rows.isEmpty()) {
            throw new CsvValidationException(List.of("No transactions found in the uploaded file."));
        }
        return rows;
    }

    private static void parseRecord(CSVRecord csvRecord, int fileLine, List<String> errors, List<ParsedTransactionRow> rows) {
        try {
            String description = csvRecord.get("Description").trim();
            String amountRaw = csvRecord.get("Amount").trim();
            if (amountRaw.isEmpty() || description.toLowerCase(Locale.ROOT).startsWith("beginning balance as of")) {
                return;
            }

            LocalDate date = CsvParsingUtils.parseDateOrRecordError(csvRecord.get("Date").trim(), fileLine, errors);
            if (date == null) {
                return;
            }

            BigDecimal signedAmount = CsvParsingUtils.parseAmountOrRecordError(amountRaw, fileLine, errors);
            if (signedAmount == null) {
                return;
            }

            Direction direction = signedAmount.signum() < 0 ? Direction.DEBIT : Direction.CREDIT;
            rows.add(new ParsedTransactionRow(fileLine, date, signedAmount.abs(), direction, description));
        } catch (RuntimeException e) {
            // Guards against a row that was split into the wrong number of columns — e.g. a
            // stray quote whose "closing" position happens to land right before a real comma,
            // which is genuinely ambiguous and can't be repaired safely.
            errors.add(CsvParsingUtils.lineError(fileLine,
                "unable to parse this row — check the quoting around commas and quote characters"));
        }
    }
}
