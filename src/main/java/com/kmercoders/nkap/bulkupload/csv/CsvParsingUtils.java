package com.kmercoders.nkap.bulkupload.csv;

import org.apache.commons.csv.CSVFormat;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;

final class CsvParsingUtils {

    static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("MM/dd/yyyy");
    static final DateTimeFormatter ISO_DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE;

    static final CSVFormat CSV_FORMAT = CSVFormat.Builder.create(CSVFormat.DEFAULT)
        .setHeader()
        .setSkipHeaderRecord(true)
        .setIgnoreSurroundingSpaces(true)
        .setTrim(true)
        .build();

    private CsvParsingUtils() {
    }

    static List<String> readLines(Reader reader) {
        try (BufferedReader bufferedReader = new BufferedReader(reader)) {
            return bufferedReader.lines().toList();
        } catch (IOException e) {
            throw new CsvValidationException(List.of("Unable to read the uploaded file."));
        }
    }

    static BigDecimal parseAmount(String raw) {
        return new BigDecimal(raw.replace(",", "").trim());
    }

    static String lineError(int fileLine, String message) {
        return "Line " + fileLine + ": " + message;
    }

    /** Returns null and records an error if {@code raw} isn't a valid MM/dd/yyyy date. */
    static LocalDate parseDateOrRecordError(String raw, int fileLine, List<String> errors) {
        return parseDateOrRecordError(raw, DATE_FORMAT, fileLine, errors);
    }

    /** Returns null and records an error if {@code raw} doesn't match the given date format. */
    static LocalDate parseDateOrRecordError(String raw, DateTimeFormatter formatter, int fileLine, List<String> errors) {
        try {
            return LocalDate.parse(raw, formatter);
        } catch (DateTimeParseException e) {
            errors.add(lineError(fileLine, "invalid date '" + raw + "'"));
            return null;
        }
    }

    /** Returns null and records an error if {@code raw} isn't a valid signed amount. */
    static BigDecimal parseAmountOrRecordError(String raw, int fileLine, List<String> errors) {
        try {
            return parseAmount(raw);
        } catch (NumberFormatException e) {
            errors.add(lineError(fileLine, "invalid amount '" + raw + "'"));
            return null;
        }
    }

    /**
     * Joins the given lines into CSV text, first repairing any quote character that
     * appears inside a quoted field but isn't itself a delimiter-escaping quote (i.e.
     * a quote that isn't RFC4180-doubled). Real bank exports sometimes emit these when
     * a memo/description contains its own quote marks, e.g. {@code "for "T-Mobile bill""}
     * instead of the correctly escaped {@code "for ""T-Mobile bill"""}. Since the quoted
     * text in these fields never itself contains the delimiter, the only reason for the
     * quoting is to wrap the field — so a stray internal quote can safely be escaped
     * without ambiguity.
     */
    static String buildCsvBody(List<String> lines) {
        return lines.stream()
            .map(CsvParsingUtils::repairUnescapedQuotes)
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");
    }

    private static String repairUnescapedQuotes(String line) {
        StringBuilder repaired = new StringBuilder(line.length());
        boolean inQuotes = false;
        boolean atFieldStart = true;
        int i = 0;

        while (i < line.length()) {
            char c = line.charAt(i);

            if (inQuotes && c == '"') {
                QuoteOutcome outcome = handleQuoteInsideField(line, i, repaired);
                inQuotes = outcome.stillInQuotes();
                i = outcome.nextIndex();
                continue;
            }

            repaired.append(c);
            if (c == '"' && atFieldStart) {
                inQuotes = true;
                atFieldStart = false;
            } else {
                atFieldStart = (c == ',');
            }
            i++;
        }

        return repaired.toString();
    }

    private record QuoteOutcome(int nextIndex, boolean stillInQuotes) {
    }

    /**
     * Called with the cursor on a quote character while already inside a quoted field.
     * Distinguishes a real closing quote (followed by the delimiter or end of line) and
     * an already-escaped quote ({@code ""}) from a stray internal quote, which is repaired
     * by doubling it.
     */
    private static QuoteOutcome handleQuoteInsideField(String line, int i, StringBuilder repaired) {
        char next = (i + 1 < line.length()) ? line.charAt(i + 1) : '\0';

        if (next == '"') {
            repaired.append("\"\"");
            return new QuoteOutcome(i + 2, true);
        }
        if (next == ',' || next == '\0') {
            repaired.append('"');
            return new QuoteOutcome(i + 1, false);
        }
        repaired.append("\"\"");
        return new QuoteOutcome(i + 1, true);
    }
}
