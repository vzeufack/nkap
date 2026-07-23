package com.kmercoders.nkap.account;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.transaction.TransactionRepository;
import com.kmercoders.nkap.transaction.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Service
@Transactional(readOnly = true)
public class AccountService {

    private final AccountRepository accountRepository;
    private final AppUserService appUserService;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final BudgetRepository budgetRepository;

    public AccountService(AccountRepository accountRepository, AppUserService appUserService,
                          TransactionRepository transactionRepository, TransactionService transactionService,
                          BudgetRepository budgetRepository) {
        this.accountRepository     = accountRepository;
        this.appUserService        = appUserService;
        this.transactionRepository = transactionRepository;
        this.transactionService    = transactionService;
        this.budgetRepository      = budgetRepository;
    }

    public List<AccountDTO> getAccountsForCurrentUser() {
        AppUser appUser = appUserService.getAuthenticatedUser();
        return accountRepository.findByAppUser(appUser).stream()
            .map(AccountDTO::from)
            .sorted(Comparator.comparing(dto -> dto.getName().toLowerCase()))
            .toList();
    }

    public Set<Long> getAccountIdsWithTransactionsForCurrentUser() {
        AppUser appUser = appUserService.getAuthenticatedUser();
        return transactionRepository.findAccountIdsWithTransactionsByAppUserId(appUser.getId());
    }

    @Transactional
    public AccountDTO createAccount(AccountRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = new Account(request.getAccountType(), request.getName(), BigDecimal.ZERO, appUser);
        accountRepository.save(account);

        if (request.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            Budget budget = resolveBudgetForAdjustment(appUser);
            transactionService.createAdjustmentTransaction(
                budget, account, null, request.getBalance(), "Initial balance adjustment for new account");
        }

        return AccountDTO.from(account);
    }

    @Transactional
    public AccountDTO updateAccount(Long id, AccountRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = accountRepository.findByIdAndAppUser(id, appUser)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
        account.setName(request.getName());
        account.setType(request.getAccountType());

        BigDecimal delta = request.getBalance().subtract(account.getBalance());
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            Budget budget = resolveBudgetForAdjustment(appUser);
            transactionService.createAdjustmentTransaction(
                budget, account, null, delta, "Balance adjustment for account update");
        }

        return AccountDTO.from(account);
    }

    private Budget resolveBudgetForAdjustment(AppUser appUser) {
        LocalDate today = LocalDate.now();
        return budgetRepository.findByAppUserIdAndMonthAndYear(appUser.getId(), today.getMonth(), today.getYear())
            .or(() -> budgetRepository.findLastBudgetByAppUser(appUser))
            .orElseThrow(() -> new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Create a budget before setting a non-zero account balance."));
    }

    @Transactional
    public void deleteAccount(Long id) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Account account = accountRepository.findByIdAndAppUser(id, appUser)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));

        if (account.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot delete an account with a non-zero balance.");
        }

        if (transactionRepository.existsByAccountId(account.getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot delete an account that has transactions linked to it.");
        }

        accountRepository.delete(account);
    }
}
