package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.category.BudgetCategory;

import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    public BudgetController(BudgetService budgetService, AppUserService appUserService) {
        this.budgetService = budgetService;
        this.appUserService = appUserService;
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
        
        if (budget != null) {
            Map<Long, List<BudgetCategory>> categoriesByGroup = budget.getBudgetCategories()
                .stream()
                .sorted(Comparator.comparing(bc -> bc.getCategory().getName()))
                .collect(Collectors.groupingBy(
                    bc -> bc.getCategory().getGroup().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            model.addAttribute("categoriesByGroup", categoriesByGroup);
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

        if (budget != null) {
            Map<Long, List<BudgetCategory>> categoriesByGroup = budget.getBudgetCategories()
                .stream()
                .sorted(Comparator.comparing(bc -> bc.getCategory().getName()))
                .collect(Collectors.groupingBy(
                    bc -> bc.getCategory().getGroup().getId(),
                    LinkedHashMap::new,
                    Collectors.toList()
                ));
            model.addAttribute("categoriesByGroup", categoriesByGroup);
        }

        return htmxRequest != null ? "fragments/budget-plan :: budget-plan" : "home";
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