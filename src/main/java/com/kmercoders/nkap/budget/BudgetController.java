package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.account.AccountDTO;
import com.kmercoders.nkap.account.AccountService;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.transaction.TransactionService;
import com.kmercoders.nkap.transaction.TransactionSummaryDTO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final AppUserService appUserService;
    private final AccountService accountService;
    private final TransactionService transactionService;

    public BudgetController(BudgetService budgetService, AppUserService appUserService,
                            AccountService accountService, TransactionService transactionService) {
        this.budgetService      = budgetService;
        this.appUserService     = appUserService;
        this.accountService     = accountService;
        this.transactionService = transactionService;
    }

    @GetMapping("/")
    public String showCurrentBudget(Model model) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        LocalDate today = LocalDate.now();
        Month currentMonth = today.getMonth();
        int currentYear = today.getYear();

        Budget budget = budgetService.findByAppUserIdAndMonthAndYear(appUser.getId(), currentMonth, currentYear);
        model.addAttribute("appUser", appUser);
        model.addAttribute("budget", budget);
        model.addAttribute("currentMonth", currentMonth + " " + currentYear);
        model.addAttribute("currentMonthValue", currentMonth.name());
        model.addAttribute("currentYearValue", currentYear);
        addAccountAttributes(model);

        if (budget != null) {
            Map<Long, List<BudgetCategory>> categoriesByGroup = budget.getBudgetCategories()
                .stream()
                .sorted(Comparator
                    .<BudgetCategory, Boolean>comparing(bc -> !bc.getCategory().getGroup().isDefault())
                    .thenComparing(bc -> bc.getCategory().getGroup().getName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(bc -> bc.getCategory().getName(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.groupingBy(
                    bc -> bc.getCategory().getGroup().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            model.addAttribute("categoriesByGroup", categoriesByGroup);

            List<TransactionSummaryDTO> transactions = transactionService.getTransactionsForBudget(budget.getId());
            model.addAttribute("transactions", transactions);

            Set<Long> categoryIdsWithTransactions = transactions.stream()
                .map(TransactionSummaryDTO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            model.addAttribute("categoryIdsWithTransactions", categoryIdsWithTransactions);
        }

        return "home";
    }

    @GetMapping("/{month}/{year}")
    public String showBudget(@PathVariable("month") Month month,
                             @PathVariable("year") int year,
                             @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                             Model model) {
        AppUser appUser = appUserService.getAuthenticatedUser();
        Budget budget = budgetService.findByAppUserIdAndMonthAndYear(appUser.getId(), month, year);
        model.addAttribute("appUser", appUser);
        model.addAttribute("budget", budget);
        model.addAttribute("currentMonth", month + " " + year);
        model.addAttribute("currentMonthValue", month.name());
        model.addAttribute("currentYearValue", year);

        if (htmxRequest == null) {
            addAccountAttributes(model);
        }

        if (budget != null) {
            Map<Long, List<BudgetCategory>> categoriesByGroup = budget.getBudgetCategories()
                .stream()
                .sorted(Comparator
                    .<BudgetCategory, Boolean>comparing(bc -> !bc.getCategory().getGroup().isDefault())
                    .thenComparing(bc -> bc.getCategory().getGroup().getName(), String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(bc -> bc.getCategory().getName(), String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.groupingBy(
                    bc -> bc.getCategory().getGroup().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            model.addAttribute("categoriesByGroup", categoriesByGroup);

            List<TransactionSummaryDTO> transactions = transactionService.getTransactionsForBudget(budget.getId());
            model.addAttribute("transactions", transactions);

            Set<Long> categoryIdsWithTransactions = transactions.stream()
                .map(TransactionSummaryDTO::getCategoryId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
            model.addAttribute("categoryIdsWithTransactions", categoryIdsWithTransactions);
        }

        return htmxRequest != null ? "fragments/budget-plan :: budget-plan" : "home";
    }

    private void addAccountAttributes(Model model) {
        List<AccountDTO> accounts = accountService.getAccountsForCurrentUser();
        BigDecimal netWorth = accounts.stream()
            .map(AccountDTO::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        model.addAttribute("accounts", accounts);
        model.addAttribute("netWorth", netWorth);
    }

    @PostMapping("/create/{month}/{year}")
    public String createBudget(@PathVariable("month") Month month,
                               @PathVariable("year") int year,
                               RedirectAttributes redirectAttributes) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        if (budgetService.existsByAppUserAndMonthAndYear(appUser, month, year)) {
            redirectAttributes.addFlashAttribute("error",
                    "A budget for " + month + " " + year + " already exists.");
            return "redirect:/budgets/" + month + "/" + year;
        }

        budgetService.createBudget(appUser, month, year);
        redirectAttributes.addFlashAttribute("success", "Budget created successfully.");
        return "redirect:/budgets/" + month + "/" + year;
    }
}