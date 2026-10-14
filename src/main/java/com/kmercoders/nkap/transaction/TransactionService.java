package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.category.BudgetCategoryRepository;
import com.kmercoders.nkap.category.Category;
import com.kmercoders.nkap.category.CategoryRepository;
import com.kmercoders.nkap.group.Group;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    public static final int PAGE_SIZE = 10;

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetService budgetService;
    private final AppUserService appUserService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              BudgetCategoryRepository budgetCategoryRepository,
                              BudgetRepository budgetRepository,
                              BudgetService budgetService,
                              AppUserService appUserService) {
        this.transactionRepository    = transactionRepository;
        this.accountRepository        = accountRepository;
        this.categoryRepository       = categoryRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetRepository         = budgetRepository;
        this.budgetService            = budgetService;
        this.appUserService           = appUserService;
    }

    public List<TransactionSummaryDTO> getTransactionsForBudget(Long budgetId) {
        return transactionRepository.findByBudgetIdOrderByTransactionDateDesc(budgetId)
            .stream()
            .map(TransactionSummaryDTO::from)
            .toList();
    }

    public Page<TransactionListItemDTO> getStandardTransactionsForCurrentUser(YearMonth period, int page) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        PageRequest pageRequest = PageRequest.of(Math.max(page, 0), PAGE_SIZE,
            Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id")));

        Page<Transaction> result = findStandardTransactions(appUser, period, pageRequest);

        // A page past the end (e.g. after deleting the only row on the last page) falls back to the last page.
        if (result.isEmpty() && result.getTotalPages() > 0 && pageRequest.getPageNumber() >= result.getTotalPages()) {
            result = findStandardTransactions(appUser, period, pageRequest.withPage(result.getTotalPages() - 1));
        }

        return result.map(TransactionListItemDTO::from);
    }

    private Page<Transaction> findStandardTransactions(AppUser appUser, YearMonth period, PageRequest pageRequest) {
        return transactionRepository.findByBudgetAppUserIdAndTransactionTypeAndTransactionDateBetween(
            appUser.getId(), TransactionType.STANDARD, period.atDay(1), period.atEndOfMonth(), pageRequest);
    }

    @Transactional
    public TransactionDTO updateTransaction(Long transactionId, TransactionRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Transaction transaction = transactionRepository.findByIdAndBudgetAppUserId(transactionId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findByIdAndAppUser(request.getAccountId(), appUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        }

        Budget budget = budgetRepository.findByIdAndAppUserId(request.getBudgetId(), appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (request.getTransactionDate() != null) {
            boolean wrongMonth = !request.getTransactionDate().getMonth().equals(budget.getMonth());
            boolean wrongYear  = request.getTransactionDate().getYear()  != budget.getYear();
            if (wrongMonth || wrongYear) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transaction date must fall within the budget month (" + budget.getMonth() + " " + budget.getYear() + ")"
                );
            }
        }

        BudgetCategory budgetCategory = null;
        if (request.getCategoryId() != null) {
            if (!categoryRepository.existsById(request.getCategoryId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
            }
            budgetCategory = budgetCategoryRepository
                .findByBudgetIdAndCategoryId(request.getBudgetId(), request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found in budget"));
        }

        return applyUpdate(transaction, request, account, budgetCategory, budget);
    }

    /**
     * Updates a transaction without the caller choosing a budget — the by-date counterpart of
     * {@link #updateTransaction}, with the same budget creation and category relinking rules as
     * {@link #createTransactionForDate}.
     */
    @Transactional
    public TransactionDTO updateTransactionForDate(Long transactionId, TransactionDetailsRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Transaction transaction = transactionRepository.findByIdAndBudgetAppUserId(transactionId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        DatedTarget target = resolveDatedTarget(appUser, request);
        return applyUpdate(transaction, request, target.account(), target.budgetCategory(), target.budget());
    }

    private TransactionDTO applyUpdate(Transaction transaction, TransactionDetailsRequest request, Account account,
                                       BudgetCategory budgetCategory, Budget budget) {
        BigDecimal oldSignedAmount = signedAmount(transaction.getAmount(), transaction.getDirection());
        applyBalanceDelta(transaction.getAccount(), transaction.getBudgetCategory(), oldSignedAmount.negate());
        applyBalanceDelta(account, budgetCategory, signedAmount(request.getAmount(), request.getDirection()));

        transaction.setAmount(request.getAmount());
        transaction.setTransactionDate(request.getTransactionDate());
        transaction.setDirection(request.getDirection());
        transaction.setDescription(request.getDescription());
        transaction.setNote(request.getNote());
        transaction.setAccount(account);
        transaction.setBudgetCategory(budgetCategory);
        transaction.setBudget(budget);

        return TransactionDTO.from(transactionRepository.save(transaction));
    }

    @Transactional
    public TransactionDTO createTransaction(TransactionRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findByIdAndAppUser(request.getAccountId(), appUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        }

        Budget budget = budgetRepository.findByIdAndAppUserId(request.getBudgetId(), appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (request.getTransactionDate() != null) {
            boolean wrongMonth = !request.getTransactionDate().getMonth().equals(budget.getMonth());
            boolean wrongYear  = request.getTransactionDate().getYear()  != budget.getYear();
            if (wrongMonth || wrongYear) {
                throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Transaction date must fall within the budget month (" + budget.getMonth() + " " + budget.getYear() + ")"
                );
            }
        }

        BudgetCategory budgetCategory = null;
        if (request.getCategoryId() != null) {
            if (!categoryRepository.existsById(request.getCategoryId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
            }
            budgetCategory = budgetCategoryRepository
                .findByBudgetIdAndCategoryId(request.getBudgetId(), request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found in budget"));
        }

        return saveStandardTransaction(request, account, budgetCategory, budget);
    }

    /**
     * Creates a STANDARD transaction without the caller choosing a budget: the budget for the
     * transaction date's month is used, and created if it doesn't exist yet. A category that
     * isn't linked to that budget (never was, or was unlinked) is linked to it again, along
     * with its group, with a zero allocation.
     */
    @Transactional
    public TransactionDTO createTransactionForDate(TransactionDetailsRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        DatedTarget target = resolveDatedTarget(appUser, request);
        return saveStandardTransaction(request, target.account(), target.budgetCategory(), target.budget());
    }

    /** Account, budget and budget-category a by-date create/update resolves to. */
    private record DatedTarget(Account account, Budget budget, BudgetCategory budgetCategory) {}

    private DatedTarget resolveDatedTarget(AppUser appUser, TransactionDetailsRequest request) {
        // Ownership checks run before the budget lookup so a rejected request never
        // leaves an auto-created budget behind.
        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findByIdAndAppUser(request.getAccountId(), appUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            if (!categoryRepository.existsByIdAndGroup_Budgets_AppUserId(request.getCategoryId(), appUser.getId())) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found");
            }
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        }

        LocalDate date = request.getTransactionDate();
        Budget budget = budgetRepository.findByAppUserIdAndMonthAndYear(appUser.getId(), date.getMonth(), date.getYear())
            .orElseGet(() -> budgetService.createBudget(appUser, date.getMonth(), date.getYear()));

        BudgetCategory budgetCategory = null;
        if (category != null) {
            budgetCategory = findOrLinkBudgetCategory(budget, category);
        }

        return new DatedTarget(account, budget, budgetCategory);
    }

    private BudgetCategory findOrLinkBudgetCategory(Budget budget, Category category) {
        return budgetCategoryRepository.findByBudgetIdAndCategoryId(budget.getId(), category.getId())
            .orElseGet(() -> {
                Group group = category.getGroup();
                if (!budget.getGroups().contains(group)) {
                    budget.addGroup(group);
                }
                return budgetCategoryRepository.save(new BudgetCategory(budget, category, BigDecimal.ZERO));
            });
    }

    private TransactionDTO saveStandardTransaction(TransactionDetailsRequest request, Account account,
                                                   BudgetCategory budgetCategory, Budget budget) {
        Transaction transaction = new Transaction(
            request.getAmount(),
            request.getTransactionDate(),
            request.getDirection(),
            TransactionType.STANDARD,
            request.getNote(),
            account,
            budgetCategory,
            budget
        );
        transaction.setDescription(request.getDescription());

        applyBalanceDelta(account, budgetCategory, signedAmount(request.getAmount(), request.getDirection()));

        return TransactionDTO.from(transactionRepository.save(transaction));
    }

    @Transactional
    public void createAdjustmentTransaction(Budget budget, Account account, BudgetCategory budgetCategory, BigDecimal amount, String description) {
        createLegTransaction(budget, account, budgetCategory, amount, TransactionType.ADJUSTMENT, null, description);
    }

    @Transactional
    public void createCategoryTransfer(Budget budget, BudgetCategory source, BudgetCategory target, BigDecimal amount) {
        UUID transferId = UUID.randomUUID();
        createLegTransaction(budget, null, source, amount.negate(), TransactionType.TRANSFER, transferId,
            "Transfer to " + target.getCategory().getName());
        createLegTransaction(budget, null, target, amount, TransactionType.TRANSFER, transferId,
            "Transfer from " + source.getCategory().getName());
    }

    private void createLegTransaction(Budget budget, Account account, BudgetCategory budgetCategory,
                                       BigDecimal signedAmount, TransactionType type, UUID transferId, String description) {
        Direction direction = signedAmount.signum() < 0 ? Direction.DEBIT : Direction.CREDIT;

        Transaction transaction = new Transaction(
            signedAmount.abs(),
            adjustmentTransactionDate(budget),
            direction,
            type,
            null,
            account,
            budgetCategory,
            budget
        );
        transaction.setDescription(description);
        transaction.setTransferId(transferId);

        applyBalanceDelta(account, budgetCategory, signedAmount);

        transactionRepository.save(transaction);
    }

    private LocalDate adjustmentTransactionDate(Budget budget) {
        LocalDate today = LocalDate.now();
        YearMonth budgetMonth = YearMonth.of(budget.getYear(), budget.getMonth());
        YearMonth currentMonth = YearMonth.from(today);

        if (budgetMonth.equals(currentMonth)) {
            return today;
        }
        return budgetMonth.isAfter(currentMonth) ? budgetMonth.atDay(1) : budgetMonth.atEndOfMonth();
    }

    @Transactional
    public int createTransactionsBulk(Account account, List<BulkTransactionInput> inputs) {
        List<Transaction> transactions = inputs.stream()
            .map(input -> {
                Transaction transaction = new Transaction(
                    input.amount(),
                    input.transactionDate(),
                    input.direction(),
                    TransactionType.STANDARD,
                    null,
                    account,
                    null,
                    input.budget()
                );
                transaction.setDescription(input.description());
                applyBalanceDelta(account, null, signedAmount(input.amount(), input.direction()));
                return transaction;
            })
            .toList();

        transactionRepository.saveAll(transactions);
        return transactions.size();
    }

    public record BulkTransactionInput(
        BigDecimal amount,
        Direction direction,
        LocalDate transactionDate,
        String description,
        Budget budget
    ) {
    }

    @Transactional
    public void deleteTransaction(Long transactionId) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Transaction transaction = transactionRepository.findByIdAndBudgetAppUserId(transactionId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        BigDecimal signedAmount = signedAmount(transaction.getAmount(), transaction.getDirection());
        applyBalanceDelta(transaction.getAccount(), transaction.getBudgetCategory(), signedAmount.negate());

        transactionRepository.delete(transaction);
    }

    private BigDecimal signedAmount(BigDecimal amount, Direction direction) {
        return direction == Direction.DEBIT ? amount.negate() : amount;
    }

    private void applyBalanceDelta(Account account, BudgetCategory budgetCategory, BigDecimal signedAmount) {
        if (account != null) {
            account.setBalance(account.getBalance().add(signedAmount));
        }
        if (budgetCategory != null) {
            Category category = budgetCategory.getCategory();
            category.setBalance(category.getBalance().add(signedAmount));
        }
    }
}
