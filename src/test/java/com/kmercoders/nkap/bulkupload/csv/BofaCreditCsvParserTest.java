package com.kmercoders.nkap.bulkupload.csv;

import com.kmercoders.nkap.transaction.Direction;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BofaCreditCsvParserTest {

    private static final String VALID_SAMPLE = """
        Posted Date,Reference Number,Payee,Address,Amount
        07/28/2026,02305376208300344823386,"NAM DAE MUN FARMERS MA STONE MOUNTAIGA","STONE MOUNTAI GA ",-34.07
        07/27/2026,75454916207900012100014,"HONG KONG GARDEN LITHONIA GA","LITHONIA      GA ",-13.50
        07/27/2026,55432866207209205540986,"QT 747 LITHONIA GA","LITHONIA      GA ",-25.00
        07/27/2026,55421356207630195609991,"PMUSA 714086 GEORGIA T ATLANTA GA","ATLANTA       GA ",-5.45
        07/27/2026,55483826207027570273849,"WAL-MART #4472 LITHONIA GA","LITHONIA      GA ",-19.38
        07/27/2026,55432866207209055527059,"LIDL #1290 DECATUR GA","DECATUR       GA ",-20.06
        07/27/2026,55432866206208842129493,"QT 1721 ALPHARETTA GA","ALPHARETTA    GA ",-19.75
        07/27/2026,55432866206208842129477,"QT 1721 ALPHARETTA GA","ALPHARETTA    GA ",-5.59
        07/25/2026,55432866205208506582616,"APPLE.COM/BILL CUPERTINO CA","CUPERTINO     CA ",-7.99
        07/24/2026,02703406205016910718521,"Spotify P44E986362 New York NY","New York      NY ",-18.99
        07/22/2026,02305376203500372057345,"TST* STUDIO MOVIE GRIL DULUTH GA","DULUTH        GA ",-21.02
        07/22/2026,55316586203830755948797,"BP#5818521COVINGTONQPS LITHONIA GA","LITHONIA      GA ",-38.26
        07/20/2026,55483826200027244766662,"WAL-MART #1340 LITHONIA GA","LITHONIA      GA ",-102.48
        07/20/2026,19920401050086416139264,"PAYMENT FROM CHK 7216 CONF#tv5m2zefo","",554.57
        07/18/2026,55432866199206560898520,"QDOBA 2605 ALPHARETTA GA","ALPHARETTA    GA ",-11.84
        """;

    private final BofaCreditCsvParser parser = new BofaCreditCsvParser();

    @Test
    void parse_withValidSample_returnsAllRows() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        assertThat(rows).hasSize(15);
    }

    @Test
    void parse_withValidSample_mapsDateDirectionAndDescription() {
        List<ParsedTransactionRow> rows = parser.parse(new StringReader(VALID_SAMPLE));

        ParsedTransactionRow first = rows.get(0);
        assertThat(first.transactionDate()).isEqualTo(LocalDate.of(2026, 7, 28));
        assertThat(first.amount()).isEqualByComparingTo(new BigDecimal("34.07"));
        assertThat(first.direction()).isEqualTo(Direction.DEBIT);
        assertThat(first.description()).isEqualTo("NAM DAE MUN FARMERS MA STONE MOUNTAIGA");

        ParsedTransactionRow payment = rows.stream()
            .filter(r -> r.description().startsWith("PAYMENT FROM CHK"))
            .findFirst().orElseThrow();
        assertThat(payment.direction()).isEqualTo(Direction.CREDIT);
        assertThat(payment.amount()).isEqualByComparingTo(new BigDecimal("554.57"));
        // Description is the Payee field only — the Address column is ignored.
        assertThat(payment.description()).isEqualTo("PAYMENT FROM CHK 7216 CONF#tv5m2zefo");
    }

    @Test
    void parse_withSingleDigitMonthAndDay_parsesCorrectly() {
        String sample = """
            Posted Date,Reference Number,Payee,Address,Amount
            9/1/2026,02305376208300344823386,"NAM DAE MUN FARMERS MA STONE MOUNTAIGA","STONE MOUNTAI GA ",-34.07
            9/28/2026,75454916207900012100014,"HONG KONG GARDEN LITHONIA GA","LITHONIA      GA ",-13.50
            """;

        List<ParsedTransactionRow> rows = parser.parse(new StringReader(sample));

        assertThat(rows).hasSize(2);
        assertThat(rows.get(0).transactionDate()).isEqualTo(LocalDate.of(2026, 9, 1));
        assertThat(rows.get(1).transactionDate()).isEqualTo(LocalDate.of(2026, 9, 28));
    }

    @Test
    void parse_withInvalidMonth_recordsDateError() {
        String sample = """
            Posted Date,Reference Number,Payee,Address,Amount
            13/01/2026,02305376208300344823386,"NAM DAE MUN FARMERS MA STONE MOUNTAIGA","STONE MOUNTAI GA ",-34.07
            """;

        assertThatThrownBy(() -> parser.parse(new StringReader(sample)))
            .isInstanceOf(CsvValidationException.class)
            .satisfies(e -> assertThat(((CsvValidationException) e).getErrors())
                .anyMatch(msg -> msg.contains("invalid date")));
    }

    @Test
    void parse_withWrongHeader_throwsValidationError() {
        String wrongFormat = "Date,Description,Amount,Running Bal.\n07/09/2026,Some transaction,\"-18.35\",\"3,120.24\"\n";

        assertThatThrownBy(() -> parser.parse(new StringReader(wrongFormat)))
            .isInstanceOf(CsvValidationException.class)
            .satisfies(e -> assertThat(((CsvValidationException) e).getErrors())
                .anyMatch(msg -> msg.contains("Unrecognized file format")));
    }
}
