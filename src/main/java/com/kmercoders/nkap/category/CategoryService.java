package com.kmercoders.nkap.category;

import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetRepository budgetRepository;
    private final GroupRepository groupRepository;

    public CategoryService(CategoryRepository categoryRepository,
                           BudgetCategoryRepository budgetCategoryRepository,
                           BudgetRepository budgetRepository,
                           GroupRepository groupRepository) {
        this.categoryRepository       = categoryRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetRepository         = budgetRepository;
        this.groupRepository          = groupRepository;
    }

    @Transactional
    public CategoryDTO createCategory(Long budgetId, Long groupId, CategoryRequest request) {
        Budget budget = budgetRepository.findById(budgetId)
            .orElseThrow(() -> new IllegalArgumentException("Budget not found"));

        Group group = groupRepository.findById(groupId)
            .orElseThrow(() -> new IllegalArgumentException("Group not found"));

        if (!groupRepository.existsByIdAndBudgetsId(groupId, budgetId)) {
            throw new IllegalStateException("Group does not belong to this budget");
        }

        BigDecimal initialBalance = request.getBalance() != null
            ? request.getBalance()
            : BigDecimal.ZERO;

        Category category = new Category(request.getName(), group);
        category.setBalance(initialBalance);
        categoryRepository.save(category);

        BigDecimal initialAllocation = request.getAllocation() != null
            ? request.getAllocation()
            : BigDecimal.ZERO;

        BudgetCategory budgetCategory = new BudgetCategory(budget, category, initialAllocation);
        budgetCategoryRepository.save(budgetCategory);

        return CategoryDTO.from(budgetCategory);
    }

    public List<CategoryDTO> getCategoriesForBudget(Long budgetId, Long groupId) {
        return budgetCategoryRepository.findByBudgetId(budgetId).stream()
            .filter(bc -> bc.getCategory().getGroup().getId().equals(groupId))
            .map(CategoryDTO::from)
            .toList();
    }
}