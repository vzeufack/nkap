package com.kmercoders.nkap.category;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupRepository;
import com.kmercoders.nkap.transaction.Direction;
import com.kmercoders.nkap.transaction.Transaction;
import com.kmercoders.nkap.transaction.TransactionRepository;
import com.kmercoders.nkap.transaction.TransactionService;
import com.kmercoders.nkap.transaction.TransactionType;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

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
    public AutoAllocateResponse autoAllocateIncome(Long budgetId) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Budget budget = budgetRepository.findByIdAndAppUserId(budgetId, appUser.getId())
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        AllocationPlan plan = buildAllocationPlan(budget);
        for (AllocationPlanEntry entry : plan.entries()) {
            transactionService.createCategoryTransfer(budget, entry.source(), entry.target(), entry.amount());
        }

        return new AutoAllocateResponse(plan.totalAllocated(), plan.entries().size(), plan.remainingUnallocated());
    }

    /**
     * Whether an auto-allocate run against this budget would move any money right now - i.e.
     * there's realized income sitting unallocated in an income category AND at least one
     * expense category hasn't yet received its full planned allocation from income. Used to
     * drive the auto-allocate button's disabled state.
     */
    public boolean hasFundsToAutoAllocate(Budget budget) {
        return !buildAllocationPlan(budget).entries().isEmpty();
    }

    private AllocationPlan buildAllocationPlan(Budget budget) {
        Comparator<BudgetCategory> byGroupThenCategoryName = Comparator
            .<BudgetCategory, String>comparing(bc -> bc.getCategory().getGroup().getName(), String.CASE_INSENSITIVE_ORDER)
            .thenComparing(bc -> bc.getCategory().getName(), String.CASE_INSENSITIVE_ORDER);

        List<BudgetCategory> budgetCategories = budgetCategoryRepository.findByBudgetId(budget.getId());

        List<BudgetCategory> incomeCategories = budgetCategories.stream()
            .filter(bc -> bc.getCategory().getGroup().isDefault())
            .sorted(byGroupThenCategoryName)
            .toList();

        List<BudgetCategory> expenseCategories = budgetCategories.stream()
            .filter(bc -> !bc.getCategory().getGroup().isDefault())
            .sorted(byGroupThenCategoryName)
            .toList();

        if (incomeCategories.isEmpty() || expenseCategories.isEmpty()) {
            return new AllocationPlan(List.of(), ZERO_AMOUNT, ZERO_AMOUNT);
        }

        List<BigDecimal> incomeAvailable = incomeCategories.stream()
            .map(bc -> bc.getCategory().getBalance().max(BigDecimal.ZERO))
            .collect(Collectors.toCollection(ArrayList::new));

        BigDecimal pool = incomeAvailable.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        if (pool.compareTo(BigDecimal.ZERO) <= 0) {
            return new AllocationPlan(List.of(), ZERO_AMOUNT, ZERO_AMOUNT);
        }

        Map<Long, BigDecimal> netFundedFromIncome =
            computeNetFundedFromIncome(budget.getId(), incomeCategories, expenseCategories);

        List<AllocationPlanEntry> entries = new ArrayList<>();
        BigDecimal totalAllocated = BigDecimal.ZERO;
        int incomeIndex = 0;

        for (BudgetCategory expense : expenseCategories) {
            BigDecimal remainingNeed = expense.getAllocation()
                .subtract(netFundedFromIncome.getOrDefault(expense.getId(), BigDecimal.ZERO));

            while (remainingNeed.compareTo(BigDecimal.ZERO) > 0 && incomeIndex < incomeCategories.size()) {
                BigDecimal available = incomeAvailable.get(incomeIndex);
                if (available.compareTo(BigDecimal.ZERO) <= 0) {
                    incomeIndex++;
                    continue;
                }

                BigDecimal amount = remainingNeed.min(available);
                entries.add(new AllocationPlanEntry(incomeCategories.get(incomeIndex), expense, amount));

                incomeAvailable.set(incomeIndex, available.subtract(amount));
                remainingNeed = remainingNeed.subtract(amount);
                totalAllocated = totalAllocated.add(amount);

                if (incomeAvailable.get(incomeIndex).compareTo(BigDecimal.ZERO) <= 0) {
                    incomeIndex++;
                }
            }

            if (incomeIndex >= incomeCategories.size()) {
                break;
            }
        }

        BigDecimal remainingUnallocated = pool.subtract(totalAllocated);
        return new AllocationPlan(entries, totalAllocated, remainingUnallocated);
    }

    private record AllocationPlanEntry(BudgetCategory source, BudgetCategory target, BigDecimal amount) {}

    private record AllocationPlan(List<AllocationPlanEntry> entries, BigDecimal totalAllocated, BigDecimal remainingUnallocated) {}

    /**
     * For every past TRANSFER (whether created by a manual category transfer or a previous
     * auto-allocate run) that moved money between an income category and an expense category,
     * nets the signed amount at the expense category's leg. This is deliberately based on
     * transfer history rather than the expense category's current balance, since balance is
     * also reduced by spending - using it would make an already-spent-down category look like
     * it still needs funding from income.
     */
    private Map<Long, BigDecimal> computeNetFundedFromIncome(
            Long budgetId, List<BudgetCategory> incomeCategories, List<BudgetCategory> expenseCategories) {

        Set<Long> incomeIds = incomeCategories.stream().map(BudgetCategory::getId).collect(Collectors.toSet());
        Set<Long> expenseIds = expenseCategories.stream().map(BudgetCategory::getId).collect(Collectors.toSet());

        List<Transaction> transferLegs =
            transactionRepository.findByBudgetIdAndTransactionType(budgetId, TransactionType.TRANSFER);

        Map<UUID, List<Transaction>> legsByTransferId = transferLegs.stream()
            .filter(t -> t.getTransferId() != null)
            .collect(Collectors.groupingBy(Transaction::getTransferId));

        Map<Long, BigDecimal> netFundedFromIncome = new HashMap<>();

        for (List<Transaction> legs : legsByTransferId.values()) {
            if (legs.size() != 2) {
                continue; // defensive; category transfers always create exactly 2 legs
            }

            Transaction legA = legs.get(0);
            Transaction legB = legs.get(1);
            Transaction expenseLeg = null;

            if (isInCategorySet(legA, incomeIds) && isInCategorySet(legB, expenseIds)) {
                expenseLeg = legB;
            } else if (isInCategorySet(legB, incomeIds) && isInCategorySet(legA, expenseIds)) {
                expenseLeg = legA;
            }

            if (expenseLeg == null) {
                continue; // both legs on the same side (or unrelated) - not an income-to-expense transfer
            }

            BigDecimal signedAmount = expenseLeg.getDirection() == Direction.CREDIT
                ? expenseLeg.getAmount()
                : expenseLeg.getAmount().negate();

            netFundedFromIncome.merge(expenseLeg.getBudgetCategory().getId(), signedAmount, BigDecimal::add);
        }

        return netFundedFromIncome;
    }

    private boolean isInCategorySet(Transaction transaction, Set<Long> budgetCategoryIds) {
        return transaction.getBudgetCategory() != null
            && budgetCategoryIds.contains(transaction.getBudgetCategory().getId());
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