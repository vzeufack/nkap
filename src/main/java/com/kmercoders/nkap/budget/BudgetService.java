package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.group.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final GroupRepository groupRepository;

    public BudgetService(BudgetRepository budgetRepository, GroupRepository groupRepository) {
        this.budgetRepository = budgetRepository;
        this.groupRepository = groupRepository;
    }

    public boolean existsByAppUserAndMonthAndYear(AppUser appUser, Month month, int year) {
        return budgetRepository.existsByAppUserAndMonthAndYear(appUser, month, year);
    }

    public Budget findByAppUserIdAndMonthAndYear(Long appUserId, Month month, int year) {
        return budgetRepository
        .findByAppUserIdAndMonthAndYearWithGroups(appUserId, month, year)
        .orElse(null);
    }

    @Transactional
    public Budget createBudget(AppUser appUser, Month month, int year) {
        Budget budget = new Budget(month, year, appUser);
        budgetRepository.save(budget);

        boolean hasDefaultGroup = groupRepository.existsByBudgets_AppUserAndIsDefaultTrue(appUser);
        if (!hasDefaultGroup) {
            Group incomeGroup = new Group("Income");
            incomeGroup.setDefault(true);
            incomeGroup.getBudgets().add(budget);
            groupRepository.save(incomeGroup);
            budget.getGroups().add(incomeGroup);
        }

        return budget;
    }
}