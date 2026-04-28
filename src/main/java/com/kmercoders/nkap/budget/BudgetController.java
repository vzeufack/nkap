package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/users/{userId}/budgets")
public class BudgetController {

   private final BudgetRepository budgetRepository;
   private final AppUserRepository userRepository;

   public BudgetController(BudgetRepository budgetRepository, AppUserRepository userRepository) {
      this.budgetRepository = budgetRepository;
      this.userRepository = userRepository;
   }

   // GET /users/{userId}/budgets
   @GetMapping
   public String listBudgets(@PathVariable Long userId, Model model) {
      AppUser user = userRepository.findById(userId)
               .orElseThrow(() -> new RuntimeException("User not found"));
      model.addAttribute("user", user);
      model.addAttribute("budgets", budgetRepository.findByUserId(userId));
      return "budgets/list";
   }

   // GET /users/{userId}/budgets/new
   // @GetMapping("/new")
   // public String showCreateForm(@PathVariable Long userId, Model model) {
   //    AppUser user = userRepository.findById(userId)
   //             .orElseThrow(() -> new RuntimeException("User not found"));
   //    model.addAttribute("user", user);
   //    model.addAttribute("budget", new Budget());
   //    return "budgets/form";
   // }

   // POST /users/{userId}/budgets
   @PostMapping
   public String createBudget(@PathVariable Long userId,
                              @ModelAttribute Budget budget,
                              RedirectAttributes redirectAttributes) {
      AppUser user = userRepository.findById(userId)
               .orElseThrow(() -> new RuntimeException("User not found"));
      budget.setAppUser(user);
      budgetRepository.save(budget);
      redirectAttributes.addFlashAttribute("success", "Budget created successfully.");
      return "redirect:/users/" + userId + "/budgets";
   }

   // GET /users/{userId}/budgets/{id}/edit
   @GetMapping("/{id}/edit")
   public String showEditForm(@PathVariable Long userId,
                              @PathVariable Long id,
                              Model model) {
      AppUser user = userRepository.findById(userId)
               .orElseThrow(() -> new RuntimeException("User not found"));
      Budget budget = budgetRepository.findByIdAndUserId(id, userId)
               .orElseThrow(() -> new RuntimeException("Budget not found"));
      model.addAttribute("user", user);
      model.addAttribute("budget", budget);
      return "budgets/form";
   }
}
