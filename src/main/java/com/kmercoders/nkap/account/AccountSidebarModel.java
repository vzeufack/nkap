package com.kmercoders.nkap.account;

import com.kmercoders.nkap.category.CategoryService;
import com.kmercoders.nkap.financialinstitution.FinancialInstitutionService;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.ui.Model;

/**
 * Populates the model attributes needed by the accounts sidebar
 * ({@code fragments/accounts-sidebar}) and the add/edit account modals
 * ({@code fragments/account-modals}). Shared by every page that renders the sidebar.
 */
@Component
public class AccountSidebarModel {

    private final AccountService accountService;
    private final CategoryService categoryService;
    private final FinancialInstitutionService financialInstitutionService;

    public AccountSidebarModel(AccountService accountService, CategoryService categoryService,
                               FinancialInstitutionService financialInstitutionService) {
        this.accountService              = accountService;
        this.categoryService             = categoryService;
        this.financialInstitutionService = financialInstitutionService;
    }

    public void populate(Model model) {
        List<AccountDTO> accounts = accountService.getAccountsForCurrentUser();
        BigDecimal netWorth = accounts.stream()
            .map(AccountDTO::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal categoryBalance = categoryService.getTotalCategoryBalanceForCurrentUser();
        model.addAttribute("accounts", accounts);
        model.addAttribute("netWorth", netWorth);
        model.addAttribute("categoryBalance", categoryBalance);
        model.addAttribute("balanceDifference", netWorth.subtract(categoryBalance));
        model.addAttribute("accountIdsWithTransactions", accountService.getAccountIdsWithTransactionsForCurrentUser());
        model.addAttribute("financialInstitutions", financialInstitutionService.getAllFinancialInstitutions());
    }
}
