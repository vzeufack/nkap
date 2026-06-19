package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.group.*;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Month;
import java.util.List;
import java.util.Optional;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.category.BudgetCategoryRepository;

@Service
public class BudgetService {

    private final BudgetRepository budgetRepository;
    private final GroupRepository groupRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;

    public BudgetService(BudgetRepository budgetRepository, GroupRepository groupRepository, BudgetCategoryRepository budgetCategoryRepository) {
        this.budgetRepository = budgetRepository;
        this.groupRepository = groupRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
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
        Optional<Budget> lastBudget = budgetRepository
                .findLastBudgetByAppUser(appUser)
                .flatMap(b -> budgetRepository.findByIdWithGroupsAndCategories(b.getId()));

        Budget budget = new Budget(month, year, appUser);

        if (lastBudget.isEmpty()) {
            Group incomeGroup = new Group("Income");
            incomeGroup.setDefault(true);
            groupRepository.save(incomeGroup);
            budget.addGroup(incomeGroup);
            return budgetRepository.save(budget);
        }

        budgetRepository.save(budget);

        Budget source = lastBudget.get();

        for (Group group : source.getGroups()) {
            budget.addGroup(group);
        }

        for (BudgetCategory sourceBc : source.getBudgetCategories()) {
            BudgetCategory newBc = new BudgetCategory(
                    budget,
                    sourceBc.getCategory(),
                    sourceBc.getAllocation());
            budgetCategoryRepository.save(newBc);
        }

        return budgetRepository.save(budget);
    }
}