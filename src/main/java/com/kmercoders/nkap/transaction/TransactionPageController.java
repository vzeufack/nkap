package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.AccountService;
import com.kmercoders.nkap.account.AccountType;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.category.CategoryService;

import java.time.YearMonth;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Server-rendered transactions page. Lives alongside the JSON {@link TransactionController},
 * which only maps POST/PUT/DELETE on /transactions, so the GET here doesn't conflict.
 */
@Controller
public class TransactionPageController {

    private final TransactionService transactionService;
    private final AppUserService appUserService;
    private final AccountService accountService;
    private final CategoryService categoryService;

    public TransactionPageController(TransactionService transactionService, AppUserService appUserService,
                                     AccountService accountService, CategoryService categoryService) {
        this.transactionService = transactionService;
        this.appUserService     = appUserService;
        this.accountService     = accountService;
        this.categoryService    = categoryService;
    }

    @GetMapping("/transactions")
    public String showTransactions(@ModelAttribute("filter") TransactionFilter filter,
                                   @RequestParam(value = "page", defaultValue = "0") int page,
                                   @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                                   Model model) {
        filter.normalize();
        model.addAttribute("transactionsPage", transactionService.getTransactionsForCurrentUser(filter, page));
        model.addAttribute("rangeLabel", filter.rangeLabel(YearMonth.now()));

        if (htmxRequest != null) {
            return "fragments/transactions-table :: transactions-table";
        }

        model.addAttribute("appUser", appUserService.getAuthenticatedUser());
        model.addAttribute("accounts", accountService.getAccountsForCurrentUser());
        model.addAttribute("categoryOptions", categoryService.getCategoryOptionsForCurrentUser());
        model.addAttribute("filterGroups", categoryService.getGroupsWithCategoriesForCurrentUser());
        model.addAttribute("accountTypes", AccountType.values());
        model.addAttribute("periods", TransactionPeriod.values());
        model.addAttribute("transactionTypes", TransactionTypeFilter.values());
        return "transactions";
    }
}
