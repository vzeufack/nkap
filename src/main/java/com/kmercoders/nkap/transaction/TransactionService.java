package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.category.BudgetCategoryRepository;
import com.kmercoders.nkap.category.Category;
import com.kmercoders.nkap.category.CategoryRepository;
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

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetRepository budgetRepository;
    private final AppUserService appUserService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              BudgetCategoryRepository budgetCategoryRepository,
                              BudgetRepository budgetRepository,
                              AppUserService appUserService) {
        this.transactionRepository    = transactionRepository;
        this.accountRepository        = accountRepository;
        this.categoryRepository       = categoryRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetRepository         = budgetRepository;
        this.appUserService           = appUserService;
    }

    public List<TransactionSummaryDTO> getTransactionsForBudget(Long budgetId) {
        return transactionRepository.findByBudgetIdOrderByTransactionDateDesc(budgetId)
            .stream()
            .map(TransactionSummaryDTO::from)
            .toList();
    }

    @Transactional
    public TransactionDTO updateTransaction(Long transactionId, TransactionRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Transaction transaction = transactionRepository.findByIdAndBudgetAppUserId(transactionId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));

        Account oldAccount = transaction.getAccount();
        BudgetCategory oldBudgetCategory = transaction.getBudgetCategory();
        BigDecimal oldSignedAmount = signedAmount(transaction.getAmount(), transaction.getDirection());

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

        applyBalanceDelta(oldAccount, oldBudgetCategory, oldSignedAmount.negate());
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
