package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.category.BudgetCategoryRepository;
import com.kmercoders.nkap.category.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

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
            request.getTransactionType(),
            request.getNote(),
            account,
            budgetCategory,
            budget
        );
        transaction.setDescription(request.getDescription());

        return TransactionDTO.from(transactionRepository.save(transaction));
    }
}
