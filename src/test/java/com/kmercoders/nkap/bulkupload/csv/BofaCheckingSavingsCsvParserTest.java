package com.kmercoders.nkap.bulkupload.csv;

import com.kmercoders.nkap.bulkupload.csv.BofaCheckingSavingsCsvParser;
import com.kmercoders.nkap.bulkupload.csv.CsvValidationException;
import com.kmercoders.nkap.bulkupload.csv.ParsedTransactionRow;
import com.kmercoders.nkap.transaction.Direction;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BofaCheckingSavingsCsvParserTest {

    // Same content as the sample file, with the embedded quotes in Zelle memo
    // fields properly RFC4180-escaped (doubled) — how BofA's real export encodes them.
    private static final String VALID_SAMPLE = """
        Description,,Summary Amt.
        Beginning balance as of 07/09/2026,,"3,138.59"
        Total credits,,"2,997.13"
        Total debits,,"-4,743.58"
        Ending balance as of 07/29/2026,,"1,392.14"

        Date,Description,Amount,Running Bal.
        07/09/2026,Beginning balance as of 07/09/2026,,"3,138.59"
        07/09/2026,"TapTap Send US 07/08 PMNT SENT 8339160670 DE","-18.35","3,120.24"
        07/13/2026,"TapTap Send US 07/12 PMNT SENT 8339160670 DE","-87.12","3,033.12"
        07/14/2026,"Zelle Recurring payment to Hawa for ""T-Mobile bill""; Conf# yidxfcru5","-68.80","2,964.32"
        07/14/2026,"COMCAST-XFINITY DES:CABLE SVCS ID:7635267 INDN:VANNEL *ZEUFACK CO ID:0000213249 PPD","-105.20","2,859.12"
        07/17/2026,"NEXTEP PAYROLL DES:PAYROLL ID:000680 INDN:VANNEL ZEUFACK CO ID:9946759001 PPD","2,997.13","5,856.25"
        07/17/2026,"TapTap Send US 07/16 PMNT SENT 8339160670 DE","-9.64","5,846.61"
        07/20/2026,"Online Banking payment to CRD 9991 Confirmation# tv5m2zefo","-554.57","5,292.04"
        07/20/2026,"Online Banking transfer to SAV 8732 Confirmation# 7248240149","-3,200.00","2,092.04"
        07/20/2026,"CAPITAL ONE DES:ONLINE PMT ID:CA02D317D0FAFD1 INDN:Vannel Zeufack CO ID:9279744391 WEB","-19.99","2,072.05"
        07/22/2026,"Zelle payment to Soin Traore for ""Movie""; Conf# yf0li6d2q","-8.42","2,063.63"
        07/22/2026,"Zelle payment to Maman Martine for ""Pour Maman Penko Helene""; Conf# uepfrmv6y","-250.00","1,813.63"
        07/22/2026,"GPC DES:GPC EFT ID:1704848092MEE INDN:Vannel Zeufack CO ID:1580257110 PPD","-131.49","1,682.14"
        07/24/2026,"Zelle payment to Treadmill for ""Nordick Treadmill""; Conf# uccyrc9pw","-290.00","1,392.14"
        """;

    // The sample as actually attached to the feature request: the same Zelle memo
    // quotes are NOT doubled (invalid RFC4180), which the parser repairs on the fly
    // since the quoted text never contains the delimiter — there's no ambiguity to
    // resolve, so it can be parsed rather than rejected.
    private static final String SAMPLE_WITH_UNESCAPED_QUOTES = """
        Description,,Summary Amt.
        Beginning balance as of 07/09/2026,,"3,138.59"
        Total credits,,"2,997.13"
        Total debits,,"-4,743.58"
        Ending balance as of 07/29/2026,,"1,392.14"

        Date,Description,Amount,Running Bal.
        07/09/2026,Beginning balance as of 07/09/2026,,"3,138.59"
        07/09/2026,"TapTap Send US 07/08 PMNT SENT 8339160670 DE","-18.35","3,120.24"
        07/14/2026,"Zelle Recurring payment to Hawa for "T-Mobile bill"; Conf# yidxfcru5","-68.80","2,964.32"
        """;

    private final BofaCheckingSavingsCsvParser parser = new BofaCheckingSavingsCsvParser();

    @Test
    void parse_withValidSample_skipsPreambleAndBeginningBalanceRow() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        assertThat(rows).hasSize(13);
        assertThat(rows).noneMatch(r -> r.description().toLowerCase().startsWith("beginning balance"));
    }

    @Test
    void parse_withValidSample_mapsDateAmountDirectionAndDescription() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        ParsedTransactionRow first = rows.get(0);
        assertThat(first.transactionDate()).isEqualTo(LocalDate.of(2026, 7, 9));
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("18.35"));
        assertThat(first.direction()).isEqualTo(Direction.DEBIT);
        assertThat(first.description()).isEqualTo("TapTap Send US 07/08 PMNT SENT 8339160670 DE");

        ParsedTransactionRow payroll = rows.stream()
            .filter(r -> r.description().startsWith("NEXTEP PAYROLL"))
            .findFirst().orElseThrow();
        assertThat(payroll.direction()).isEqualTo(Direction.CREDIT);
        assertThat(payroll.amount()).isEqualByComparingTo(new BigDecimal("2997.13"));

        ParsedTransactionRow quotedMemo = rows.stream()
            .filter(r -> r.description().contains("Hawa"))
            .findFirst().orElseThrow();
        assertThat(quotedMemo.description()).isEqualTo("Zelle Recurring payment to Hawa for \"T-Mobile bill\"; Conf# yidxfcru5");
        assertThat(quotedMemo.amount()).isEqualByComparingTo(new BigDecimal("68.80"));
        assertThat(quotedMemo.direction()).isEqualTo(Direction.DEBIT);
    }

    @Test
    void parse_withUnescapedQuoteInDescription_repairsAndParsesCorrectly() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(SAMPLE_WITH_UNESCAPED_QUOTES));

        assertThat(rows).hasSize(2);

        ParsedTransactionRow tapTap = rows.get(0);
        assertThat(tapTap.description()).isEqualTo("TapTap Send US 07/08 PMNT SENT 8339160670 DE");
        assertThat(tapTap.amount()).isEqualByComparingTo(new BigDecimal("18.35"));
        assertThat(tapTap.direction()).isEqualTo(Direction.DEBIT);

        ParsedTransactionRow quotedMemo = rows.get(1);
        assertThat(quotedMemo.description())
            .isEqualTo("Zelle Recurring payment to Hawa for \"T-Mobile bill\"; Conf# yidxfcru5");
        assertThat(quotedMemo.amount()).isEqualByComparingTo(new BigDecimal("68.80"));
        assertThat(quotedMemo.direction()).isEqualTo(Direction.DEBIT);
    }

    @Test
    void parse_withCommaInsideStrayQuotedPhrase_stillParsesCorrectly() {
        // A comma that appears *inside* a stray-quoted phrase is not ambiguous: the
        // repair never exits "in quotes" mode until it sees a quote immediately
        // followed by a real delimiter, so an embedded comma here stays literal text.
        String sample = """
            Date,Description,Amount,Running Bal.
            07/14/2026,"He said "wow, right" to me","-10.00","900.00"
            """;

        List<ParsedTransactionRow> rows = parser.parse(new StringReader(sample));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).description()).isEqualTo("He said \"wow, right\" to me");
        assertThat(rows.get(0).amount()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(rows.get(0).direction()).isEqualTo(Direction.DEBIT);
    }

    @Test
    void parse_withCommaImmediatelyAfterStrayClosingQuote_failsGracefullyInsteadOfCorruptingData() {
        // Genuinely ambiguous: the stray quote right after "wow" is immediately
        // followed by a real comma, making it indistinguishable from an actual
        // closing quote. The repair guesses wrong and splits the row into extra
        // columns — this must surface as a validation error, not a crash or a
        // silently-wrong transaction.
        String sample = """
            Date,Description,Amount,Running Bal.
            07/14/2026,"He said "wow", to me","-10.00","900.00"
            """;

        assertThatThrownBy(() -> parser.parse(new StringReader(sample)))
            .isInstanceOf(CsvValidationException.class)
            .satisfies(e -> assertThat(((CsvValidationException) e).getErrors())
                .anyMatch(msg -> msg.startsWith("Line 2:")));
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
