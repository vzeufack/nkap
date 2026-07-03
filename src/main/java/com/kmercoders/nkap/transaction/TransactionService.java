package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.category.Category;
import com.kmercoders.nkap.category.CategoryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final CategoryRepository categoryRepository;
    private final BudgetRepository budgetRepository;
    private final AppUserService appUserService;

    public TransactionService(TransactionRepository transactionRepository,
                              AccountRepository accountRepository,
                              CategoryRepository categoryRepository,
                              BudgetRepository budgetRepository,
                              AppUserService appUserService) {
        this.transactionRepository = transactionRepository;
        this.accountRepository     = accountRepository;
        this.categoryRepository    = categoryRepository;
        this.budgetRepository      = budgetRepository;
        this.appUserService        = appUserService;
    }

    @Transactional
    public TransactionDTO createTransaction(TransactionRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Account account = null;
        if (request.getAccountId() != null) {
            account = accountRepository.findByIdAndAppUser(request.getAccountId(), appUser)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        }

        Category category = null;
        if (request.getCategoryId() != null) {
            category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found"));
        }

        Budget budget = null;
        if (request.getBudgetId() != null) {
            budget = budgetRepository.findByIdAndAppUserId(request.getBudgetId(), appUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));
        }

        Transaction transaction = new Transaction(
            request.getAmount(),
            request.getTransactionDate(),
            request.getTransactionType(),
            request.getNote(),
            account,
            category,
            budget
        );

        return TransactionDTO.from(transactionRepository.save(transaction));
    }
}
