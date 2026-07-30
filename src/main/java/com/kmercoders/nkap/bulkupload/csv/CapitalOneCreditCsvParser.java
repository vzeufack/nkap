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

/**
 * Capital One Credit export: "Transaction Date,Posted Date,Card No.,Description,
 * Category,Debit,Credit", no preamble. The transaction date is taken from Posted
 * Date (not Transaction Date). Exactly one of Debit/Credit is populated per row —
 * whichever it is determines both the direction and the amount.
 */
@Component
public class CapitalOneCreditCsvParser implements BulkUploadCsvParser {

    private static final String EXPECTED_HEADER =
        "Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit";

    @Override
    public List<ParsedTransactionRow> parse(Reader reader) {
        List<String> lines = CsvParsingUtils.readLines(reader);

        if (lines.isEmpty() || !lines.get(0).trim().equals(EXPECTED_HEADER)) {
            throw new CsvValidationException(List.of(
                "Unrecognized file format. Expected columns: " + EXPECTED_HEADER));
        }

        String csvBody = CsvParsingUtils.buildCsvBody(lines);
        List<String> errors = new ArrayList<>();
        List<ParsedTransactionRow> rows = new ArrayList<>();

        try (CSVParser parser = CsvParsingUtils.CSV_FORMAT.parse(new StringReader(csvBody))) {
            for (CSVRecord csvRecord : parser) {
                int fileLine = 1 + (int) csvRecord.getRecordNumber();
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

            LocalDate date = CsvParsingUtils.parseDateOrRecordError(
                csvRecord.get("Posted Date").trim(), CsvParsingUtils.ISO_DATE_FORMAT, fileLine, errors);
            if (date == null) {
                return;
            }

            String debitRaw = csvRecord.get("Debit").trim();
            String creditRaw = csvRecord.get("Credit").trim();

            Direction direction;
            String amountRaw;
            if (!debitRaw.isEmpty()) {
                direction = Direction.DEBIT;
                amountRaw = debitRaw;
            } else if (!creditRaw.isEmpty()) {
                direction = Direction.CREDIT;
                amountRaw = creditRaw;
            } else {
                errors.add(CsvParsingUtils.lineError(fileLine, "row has neither a debit nor a credit amount"));
                return;
            }

            BigDecimal amount = CsvParsingUtils.parseAmountOrRecordError(amountRaw, fileLine, errors);
            if (amount == null) {
                return;
            }

            rows.add(new ParsedTransactionRow(fileLine, date, amount.abs(), direction, description));
        } catch (RuntimeException e) {
            // Guards against a row that was split into the wrong number of columns — e.g. a
            // stray quote whose "closing" position happens to land right before a real comma,
            // which is genuinely ambiguous and can't be repaired safely.
            errors.add(CsvParsingUtils.lineError(fileLine,
                "unable to parse this row — check the quoting around commas and quote characters"));
        }
    }
}
