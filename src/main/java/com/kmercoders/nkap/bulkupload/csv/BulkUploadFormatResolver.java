package com.kmercoders.nkap.bulkupload.csv;

import com.kmercoders.nkap.account.AccountType;
import com.kmercoders.nkap.financialinstitution.FinancialInstitution;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.Set;

/**
 * Single seam mapping a financial institution + account type to the parser that
 * understands its CSV export. Bank of America Checking/Savings/Credit and Capital
 * One Credit are implemented; Capital One Checking/Savings resolve to empty until
 * that export format is known.
 */
@Component
public class BulkUploadFormatResolver {

    private static final String BANK_OF_AMERICA_KEY = "BANK_OF_AMERICA";
    private static final String CAPITAL_ONE_KEY = "CAPITAL_ONE";
    private static final Set<AccountType> CHECKING_SAVINGS = Set.of(AccountType.CHECKING, AccountType.SAVINGS);

    private final BofaCheckingSavingsCsvParser bofaCheckingSavingsCsvParser;
    private final BofaCreditCsvParser bofaCreditCsvParser;
    private final CapitalOneCreditCsvParser capitalOneCreditCsvParser;

    public BulkUploadFormatResolver(BofaCheckingSavingsCsvParser bofaCheckingSavingsCsvParser,
                                     BofaCreditCsvParser bofaCreditCsvParser,
                                     CapitalOneCreditCsvParser capitalOneCreditCsvParser) {
        this.bofaCheckingSavingsCsvParser = bofaCheckingSavingsCsvParser;
        this.bofaCreditCsvParser = bofaCreditCsvParser;
        this.capitalOneCreditCsvParser = capitalOneCreditCsvParser;
    }

    public Optional<BulkUploadCsvParser> resolve(FinancialInstitution institution, AccountType accountType) {
        if (institution == null || accountType == null) {
            return Optional.empty();
        }

        String institutionKey = institution.getKey();
        if (BANK_OF_AMERICA_KEY.equals(institutionKey)) {
            if (CHECKING_SAVINGS.contains(accountType)) {
                return Optional.of(bofaCheckingSavingsCsvParser);
            }
            if (accountType == AccountType.CREDIT) {
                return Optional.of(bofaCreditCsvParser);
            }
        } else if (CAPITAL_ONE_KEY.equals(institutionKey) && accountType == AccountType.CREDIT) {
            return Optional.of(capitalOneCreditCsvParser);
        }

        return Optional.empty();
    }
}
