package com.kmercoders.nkap.bulkupload;

import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;
import com.kmercoders.nkap.bulkupload.csv.BulkUploadCsvParser;
import com.kmercoders.nkap.bulkupload.csv.BulkUploadFormatResolver;
import com.kmercoders.nkap.bulkupload.csv.ParsedTransactionRow;
import com.kmercoders.nkap.transaction.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@Transactional(readOnly = true)
public class BulkUploadService {

    private final AccountRepository accountRepository;
    private final AppUserService appUserService;
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;
    private final BulkUploadFormatResolver formatResolver;
    private final TransactionService transactionService;

    public BulkUploadService(AccountRepository accountRepository,
                              AppUserService appUserService,
                              BudgetRepository budgetRepository,
                              BudgetService budgetService,
                              BulkUploadFormatResolver formatResolver,
                              TransactionService transactionService) {
        this.accountRepository = accountRepository;
        this.appUserService = appUserService;
        this.budgetRepository = budgetRepository;
        this.budgetService = budgetService;
        this.formatResolver = formatResolver;
        this.transactionService = transactionService;
    }

    public BulkUploadPreviewResponse previewUpload(Long accountId, MultipartFile file, LocalDate startDate, LocalDate endDate) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = resolveAccount(accountId, appUser);
        BulkUploadCsvParser parser = resolveParser(account);

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Please select a CSV file to upload.");
        }
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Start date must be before end date.");
        }

        List<ParsedTransactionRow> rows = parser.parse(readerFor(file));
        List<ParsedTransactionRow> filteredRows = rows.stream()
            .filter(row -> startDate == null || !row.transactionDate().isBefore(startDate))
            .filter(row -> endDate == null || !row.transactionDate().isAfter(endDate))
            .toList();
        List<ParsedTransactionRow> sortedRows = filteredRows.stream()
            .sorted(Comparator.comparing(ParsedTransactionRow::transactionDate))
            .toList();

        List<BudgetToCreateDTO> budgetsToCreate = distinctSortedMonths(filteredRows).stream()
            .filter(ym -> !budgetService.existsByAppUserAndMonthAndYear(appUser, ym.getMonth(), ym.getYear()))
            .map(ym -> new BudgetToCreateDTO(ym.getMonth(), ym.getYear()))
            .toList();

        return new BulkUploadPreviewResponse(account.getId(), sortedRows, budgetsToCreate, filteredRows.size());
    }

    @Transactional
    public BulkUploadConfirmResponse confirmUpload(BulkUploadConfirmRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = resolveAccount(request.getAccountId(), appUser);

        List<BulkUploadTransactionRowRequest> transactionRows = request.getTransactions();

        Map<YearMonth, Budget> budgetsByMonth = new TreeMap<>();
        int budgetsCreated = 0;
        List<YearMonth> months = transactionRows.stream()
            .map(row -> YearMonth.from(row.getTransactionDate()))
            .distinct()
            .sorted()
            .toList();

        for (YearMonth yearMonth : months) {
            Budget budget = budgetRepository
                .findByAppUserIdAndMonthAndYear(appUser.getId(), yearMonth.getMonth(), yearMonth.getYear())
                .orElse(null);
            if (budget == null) {
                budget = budgetService.createBudget(appUser, yearMonth.getMonth(), yearMonth.getYear());
                budgetsCreated++;
            }
            budgetsByMonth.put(yearMonth, budget);
        }

        List<TransactionService.BulkTransactionInput> inputs = transactionRows.stream()
            .map(row -> new TransactionService.BulkTransactionInput(
                row.getAmount(),
                row.getDirection(),
                row.getTransactionDate(),
                row.getDescription(),
                budgetsByMonth.get(YearMonth.from(row.getTransactionDate()))
            ))
            .toList();

        int transactionsCreated = transactionService.createTransactionsBulk(account, inputs);

        return new BulkUploadConfirmResponse(transactionsCreated, budgetsCreated);
    }

    private Account resolveAccount(Long accountId, AppUser appUser) {
        return accountRepository.findByIdAndAppUser(accountId, appUser)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private BulkUploadCsvParser resolveParser(Account account) {
        return formatResolver.resolve(account.getFinancialInstitution(), account.getType())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Bulk upload isn't supported yet for this account. Only Checking, Savings, and Credit "
                    + "accounts from Bank of America, and Credit accounts from Capital One, are currently supported."));
    }

    private List<YearMonth> distinctSortedMonths(List<ParsedTransactionRow> rows) {
        return rows.stream()
            .map(row -> YearMonth.from(row.transactionDate()))
            .distinct()
            .sorted()
            .toList();
    }

    private Reader readerFor(MultipartFile file) {
        try {
            return new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unable to read the uploaded file.");
        }
    }
}
