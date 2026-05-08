package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;

import java.time.LocalDate;
import java.time.Month;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/budgets")
public class BudgetController {

   private final BudgetRepository budgetRepository;
   private final AppUserService appUserService;

   public BudgetController(BudgetRepository budgetRepository, AppUserService appUserService) {
      this.budgetRepository = budgetRepository;
      this.appUserService = appUserService;
   }

   @GetMapping("/")
   public String showCurrentBudget(Model model) {
      AppUser appUser = appUserService.getAuthenticatedUser();
      LocalDate today = LocalDate.now();
      Month currentMonth = today.getMonth();
      int currentYear = today.getYear();

      Budget budget = budgetRepository.findByAppUserIdAndMonthAndYear(appUser.getId(), currentMonth, currentYear).orElse(null);
      model.addAttribute("appUser", appUser);
      model.addAttribute("budget", budget);
      model.addAttribute("currentMonth", currentMonth + " " + currentYear);
      model.addAttribute("currentMonthValue", currentMonth.name());        // e.g. "APRIL"
      model.addAttribute("currentYearValue", currentYear); 
      return "home";
   }

   @GetMapping("/{month}/{year}")
   public String showBudget(@PathVariable("month") Month month,
                           @PathVariable("year") int year,
                           @RequestHeader(value = "HX-Request", required = false) String htmxRequest,
                           Model model) {
      AppUser appUser = appUserService.getAuthenticatedUser();
      Budget budget = budgetRepository.findByAppUserIdAndMonthAndYear(appUser.getId(), month, year).orElse(null);
      model.addAttribute("appUser", appUser);
      model.addAttribute("budget", budget);
      model.addAttribute("currentMonth", month + " " + year);
      model.addAttribute("currentMonthValue", month.name());
      model.addAttribute("currentYearValue", year);

      return htmxRequest != null ? "fragments/budget-plan :: budget-plan" : "home";
   }

   @PostMapping("/create/{month}/{year}")
   public String createBudget(@PathVariable("month") Month month,
                              @PathVariable("year") int year,
                              RedirectAttributes redirectAttributes) {
      AppUser appUser = appUserService.getAuthenticatedUser();

      if (budgetRepository.existsByAppUserAndMonthAndYear(appUser, month, year)) {
         redirectAttributes.addFlashAttribute("error",
                  "A budget for " + month + " " + year + " already exists.");
         return "redirect:/budgets/" + month + "/" + year;
      }

      Budget budget = new Budget();
      budget.setAppUser(appUser);
      budget.setMonth(month);
      budget.setYear(year);
      budgetRepository.save(budget);
      redirectAttributes.addFlashAttribute("success", "Budget created successfully.");
      return "redirect:/budgets/" + month + "/" + year;
   }
}
