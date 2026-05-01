package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.appuser.AppUserService;

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

   @GetMapping("/{month}/{year}")
   public String showBudget(@PathVariable("month") Month month,
                           @PathVariable("year") int year,
                           Model model) {
      AppUser appUser = appUserService.getAuthenticatedUser();
      //Budget budget = budgetRepository.findByAppUserIdAndMonthAndYear(appUser.getId(), month, year)
               //.orElseThrow(() -> new RuntimeException("Budget not found"));
      model.addAttribute("appUser", appUser);
      //model.addAttribute("budget", budget);
      return "home";
   }

   // GET /users/{appUserId}/budgets
   // @GetMapping
   // public String listBudgets(@PathVariable Long appUserId, Model model) {
   //    AppUser appUser = userRepository.findById(appUserId)
   //             .orElseThrow(() -> new RuntimeException("User not found"));
   //    model.addAttribute("user", user);
   //    model.addAttribute("budgets", budgetRepository.findByAppUserId(appUserId));
   //    return "budgets/list";
   // }

   // GET /users/{appUserId}/budgets/new
   // @GetMapping("/new")
   // public String showCreateForm(@PathVariable Long appUserId, Model model) {
   //    AppUser user = userRepository.findById(appUserId)
   //             .orElseThrow(() -> new RuntimeException("User not found"));
   //    model.addAttribute("user", user);
   //    model.addAttribute("budget", new Budget());
   //    return "budgets/form";
   // }

   // POST /users/{appUserId}/budgets
   // @PostMapping
   // public String createBudget(@PathVariable Long appUserId,
   //                            @ModelAttribute Budget budget,
   //                            RedirectAttributes redirectAttributes) {
   //    AppUser user = userRepository.findById(appUserId)
   //             .orElseThrow(() -> new RuntimeException("User not found"));
   //    budget.setAppUser(user);
   //    budgetRepository.save(budget);
   //    redirectAttributes.addFlashAttribute("success", "Budget created successfully.");
   //    return "redirect:/users/" + appUserId + "/budgets";
   // }

   // // GET /users/{appUserId}/budgets/{id}/edit
   // @GetMapping("/{id}/edit")
   // public String showEditForm(@PathVariable Long appUserId,
   //                            @PathVariable Long id,
   //                            Model model) {
   //    AppUser user = userRepository.findById(appUserId)
   //             .orElseThrow(() -> new RuntimeException("User not found"));
   //    Budget budget = budgetRepository.findByIdAndAppUserId(id, appUserId)
   //             .orElseThrow(() -> new RuntimeException("Budget not found"));
   //    model.addAttribute("user", user);
   //    model.addAttribute("budget", budget);
   //    return "budgets/form";
   // }
}
