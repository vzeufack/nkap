package com.kmercoders.nkap.category;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupRepository;
import com.kmercoders.nkap.transaction.TransactionRepository;
import com.kmercoders.nkap.transaction.TransactionService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final BudgetRepository budgetRepository;
    private final GroupRepository groupRepository;
    private final TransactionRepository transactionRepository;
    private final TransactionService transactionService;
    private final AppUserService appUserService;

    public CategoryService(CategoryRepository categoryRepository,
                           BudgetCategoryRepository budgetCategoryRepository,
                           BudgetRepository budgetRepository,
                           GroupRepository groupRepository,
                           TransactionRepository transactionRepository,
                           TransactionService transactionService,
                           AppUserService appUserService) {
        this.categoryRepository       = categoryRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.budgetRepository         = budgetRepository;
        this.groupRepository          = groupRepository;
        this.transactionRepository    = transactionRepository;
        this.transactionService       = transactionService;
        this.appUserService           = appUserService;
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
        categoryRepository.save(category);

        BigDecimal initialAllocation = request.getAllocation() != null
            ? request.getAllocation()
            : BigDecimal.ZERO;

        BudgetCategory budgetCategory = new BudgetCategory(budget, category, initialAllocation);
        budgetCategoryRepository.save(budgetCategory);

        if (initialBalance.compareTo(BigDecimal.ZERO) != 0) {
            transactionService.createAdjustmentTransaction(
                budget, null, budgetCategory, initialBalance, "Initial balance adjustment for new category");
        }

        return CategoryDTO.from(budgetCategory);
    }

    public List<CategoryDTO> getCategoriesForBudget(Long budgetId, Long groupId) {
        return budgetCategoryRepository.findByBudgetId(budgetId).stream()
            .filter(bc -> bc.getCategory().getGroup().getId().equals(groupId))
            .map(CategoryDTO::from)
            .toList();
    }

    public BigDecimal getTotalCategoryBalanceForCurrentUser() {
        AppUser appUser = appUserService.getAuthenticatedUser();
        return categoryRepository.findDistinctByGroup_Budgets_AppUserId(appUser.getId()).stream()
            .map(Category::getBalance)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    @Transactional
    public CategoryDTO updateCategory(Long budgetId, Long groupId, Long categoryId, CategoryRequest request) {
        BudgetCategory bc = budgetCategoryRepository
            .findByBudgetIdAndCategoryIdAndCategoryGroupId(budgetId, categoryId, groupId)
            .orElseThrow(() -> new IllegalArgumentException("Category not found in this group."));

        bc.getCategory().setName(request.getName());

        BigDecimal updatedAllocation = request.getAllocation() != null
            ? request.getAllocation()
            : BigDecimal.ZERO;
        bc.setAllocation(updatedAllocation);

        BigDecimal updatedBalance = request.getBalance() != null
            ? request.getBalance()
            : BigDecimal.ZERO;
        BigDecimal delta = updatedBalance.subtract(bc.getCategory().getBalance());
        if (delta.compareTo(BigDecimal.ZERO) != 0) {
            transactionService.createAdjustmentTransaction(
                bc.getBudget(), null, bc, delta, "Balance adjustment for category update");
        }

        return CategoryDTO.from(bc);
    }

    @Transactional
    public CategoryTransferResponse transferBalance(Long budgetId, CategoryTransferRequest request) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Budget budget = budgetRepository.findByIdAndAppUserId(budgetId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        if (request.getSourceCategoryId().equals(request.getTargetCategoryId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Source and target categories must be different.");
        }

        BudgetCategory source = budgetCategoryRepository
            .findByBudgetIdAndCategoryId(budgetId, request.getSourceCategoryId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source category not found in this budget"));

        BudgetCategory target = budgetCategoryRepository
            .findByBudgetIdAndCategoryId(budgetId, request.getTargetCategoryId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Target category not found in this budget"));

        BigDecimal available = source.getCategory().getBalance();
        if (request.getAmount().compareTo(available) > 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Insufficient balance in " + source.getCategory().getName()
                    + ": only $" + available.toPlainString() + " is available to transfer.");
        }

        transactionService.createCategoryTransfer(budget, source, target, request.getAmount());

        return new CategoryTransferResponse(CategoryDTO.from(source), CategoryDTO.from(target));
    }

    @Transactional
    public void deleteCategory(Long budgetId, Long groupId, Long categoryId) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        budgetRepository.findByIdAndAppUserId(budgetId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        BudgetCategory bc = budgetCategoryRepository
            .findByBudgetIdAndCategoryIdAndCategoryGroupId(budgetId, categoryId, groupId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found in this group."));

        if (transactionRepository.existsByBudgetCategoryId(bc.getId())) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot delete a category that has transactions linked to it.");
        }

        Category category = bc.getCategory();
        if (category.getBalance().compareTo(BigDecimal.ZERO) != 0) {
            throw new ResponseStatusException(
                HttpStatus.BAD_REQUEST, "Cannot delete a category with a non-zero balance.");
        }

        budgetCategoryRepository.delete(bc);
        budgetCategoryRepository.flush();

        if (!budgetCategoryRepository.existsByCategoryId(category.getId())) {
            categoryRepository.delete(category);
        }
    }
}