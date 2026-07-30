package com.kmercoders.nkap.bulkupload.csv;

import com.kmercoders.nkap.transaction.Direction;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CapitalOneCreditCsvParserTest {

    private static final String VALID_SAMPLE = """
        Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
        2026-07-08,2026-07-09,2043,AMAZON MKTPLACE PMTS,Merchandise,,32.39
        2026-07-07,2026-07-07,2043,AMAZON MKTPL*CM4T050F3,Merchandise,32.39,
        2026-07-01,2026-07-01,2043,CAPITAL ONE MOBILE PYMT,Payment/Credit,,458.08
        2026-06-27,2026-06-27,2043,LAWNCARE* LAWNSTARTER,Other Services,63.39,
        2026-06-22,2026-06-23,2043,AMAZON MKTPL*P55G17403,Merchandise,32.39,
        2026-06-19,2026-06-22,2043,GW LOVEJOY COURT,Other Services,150.00,
        2026-06-19,2026-06-20,2043,GW SERV-FEE,Other Services,9.00,
        2026-06-18,2026-06-19,2043,VIVINT INC/US,Other Services,51.64,
        2026-06-18,2026-06-18,2043,CAPITAL ONE MOBILE PYMT,Payment/Credit,,241.40
        2026-06-17,2026-06-18,2043,ANTHROPIC* CLAUDE SUB,Merchandise,200.00,
        2026-06-16,2026-06-18,2043,AMAZON RETA* BB9WE0K43,Merchandise,,48.34
        2026-06-16,2026-06-17,2043,AMAZON MKTPLACE PMTS,Merchandise,,16.12
        2026-06-15,2026-06-15,2043,FuboTV Inc,Phone/Cable,55.99,
        2026-06-10,2026-06-12,2043,DISNEY EC PARKING,Entertainment,35.00,
        """;

    private final CapitalOneCreditCsvParser parser = new CapitalOneCreditCsvParser();

    @Test
    void parse_withValidSample_returnsAllRows() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        assertThat(rows).hasSize(14);
    }

    @Test
    void parse_withValidSample_usesPostedDateNotTransactionDate() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        // Transaction Date is 07/08, Posted Date is 07/09 — the row's date must be the latter.
        ParsedTransactionRow first = rows.get(0);
        assertThat(first.transactionDate()).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(first.description()).isEqualTo("AMAZON MKTPLACE PMTS");
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("32.39"));
        assertThat(first.direction()).isEqualTo(Direction.CREDIT);
    }

    @Test
    void parse_withValidSample_mapsDebitAndCreditColumnsToDirection() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        ParsedTransactionRow debitRow = rows.stream()
            .filter(r -> r.description().equals("AMAZON MKTPL*CM4T050F3"))
            .findFirst().orElseThrow();
        assertThat(debitRow.direction()).isEqualTo(Direction.DEBIT);
        assertThat(debitRow.amount()).isEqualByComparingTo(new BigDecimal("32.39"));
        assertThat(debitRow.transactionDate()).isEqualTo(LocalDate.of(2026, 7, 7));

        List<ParsedTransactionRow> capitalOnePayments = rows.stream()
            .filter(r -> r.description().equals("CAPITAL ONE MOBILE PYMT"))
            .toList();
        assertThat(capitalOnePayments).hasSize(2);
        assertThat(capitalOnePayments).allMatch(r -> r.direction() == Direction.CREDIT);
        assertThat(capitalOnePayments.get(0).amount()).isEqualByComparingTo(new BigDecimal("458.08"));
        assertThat(capitalOnePayments.get(1).amount()).isEqualByComparingTo(new BigDecimal("241.40"));
    }

    @Test
    void parse_withRowMissingBothDebitAndCredit_throwsValidationError() {
        String sample = """
            Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
            2026-07-08,2026-07-09,2043,AMAZON MKTPLACE PMTS,Merchandise,,
            """;

        assertThatThrownBy(() -> parser.parse(new StringReader(sample)))
            .isInstanceOf(CsvValidationException.class)
            .satisfies(e -> assertThat(((CsvValidationException) e).getErrors())
                .anyMatch(msg -> msg.contains("neither a debit nor a credit amount")));
    }

    @Test
    void parse_withWrongHeader_throwsValidationError() {
        String wrongFormat = "Posted Date,Reference Number,Payee,Address,Amount\n07/28/2026,123,Some Payee,Some City GA,-34.07\n";

        assertThatThrownBy(() -> parser.parse(new StringReader(wrongFormat)))
            .isInstanceOf(CsvValidationException.class)
            .satisfies(e -> assertThat(((CsvValidationException) e).getErrors())
                .anyMatch(msg -> msg.contains("Unrecognized file format")));
    }
}
