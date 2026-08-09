package com.kmercoders.nkap.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupRepository;
import com.kmercoders.nkap.transaction.Transaction;
import com.kmercoders.nkap.transaction.TransactionRepository;
import com.kmercoders.nkap.transaction.TransactionType;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryAutoAllocateControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetService budgetService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetCategoryRepository budgetCategoryRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private CategoryService categoryService;

    private static final String EMAIL = "auto_allocate_user@example.com";

    private Budget budget;
    private Group incomeGroup;
    private Group expenseGroup;

    @BeforeAll
    void createUser() {
        appUserRepository.deleteAll();
        AppUser user = new AppUser(EMAIL, passwordEncoder.encode("somepassword"));
        appUserRepository.save(user);
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        budgetCategoryRepository.deleteAll();
        categoryRepository.deleteAll();
        budgetRepository.deleteAll();
        groupRepository.deleteAll();

        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        budget = budgetService.createBudget(user, Month.JANUARY, 2025);

        incomeGroup = groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(Group::isDefault)
                .findFirst()
                .orElseThrow();

        expenseGroup = groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(g -> !g.isDefault())
                .findFirst()
                .orElseGet(() -> {
                    Group g = new Group("Expenses");
                    groupRepository.save(g);
                    budget.getGroups().add(g);
                    budgetRepository.save(budget);
                    return g;
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String autoAllocateUrl() {
        return "/budgets/%d/categories/auto-allocate".formatted(budget.getId());
    }

    private String transferUrl() {
        return "/budgets/%d/categories/transfer".formatted(budget.getId());
    }

    private String categoriesUrl(Long groupId) {
        return "/budgets/%d/groups/%d/categories".formatted(budget.getId(), groupId);
    }

    private Long createCategoryAndGetId(Long groupId, String name, BigDecimal allocation, BigDecimal balance) throws Exception {
        CategoryRequest req = new CategoryRequest();
        req.setName(name);
        req.setAllocation(allocation);
        req.setBalance(balance);

        String response = mockMvc.perform(post(categoriesUrl(groupId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    private void manualTransfer(Long sourceCategoryId, Long targetCategoryId, BigDecimal amount) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("sourceCategoryId", sourceCategoryId);
        node.put("targetCategoryId", targetCategoryId);
        node.put("amount", amount);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(node)))
                .andExpect(status().isOk());
    }

    private BigDecimal balanceOf(Long categoryId) {
        return categoryRepository.findById(categoryId).orElseThrow().getBalance();
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_fundsUnderfundedExpenseCategoriesFromIncome() throws Exception {
        Long salaryId = createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("300.00"));
        Long groceriesId = createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("100.00"), BigDecimal.ZERO);
        Long rentId = createCategoryAndGetId(expenseGroup.getId(), "Rent", new BigDecimal("150.00"), BigDecimal.ZERO);

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(250.00)))
                .andExpect(jsonPath("$.transfersCreated", is(2)))
                .andExpect(jsonPath("$.remainingUnallocated", is(50.00)));

        assertThat(balanceOf(salaryId)).isEqualByComparingTo("50.00");
        assertThat(balanceOf(groceriesId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(rentId)).isEqualByComparingTo("150.00");

        List<Transaction> transferLegs = transactionRepository.findByBudgetIdAndTransactionType(budget.getId(), TransactionType.TRANSFER);
        assertThat(transferLegs).hasSize(4); // 2 transfer pairs
    }

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_multipleIncomeCategories_drainsFirstCategoryBeforeSecond() throws Exception {
        // sorted alphabetically within the income group: "A Income" is drawn from before "B Income"
        Long aIncomeId = createCategoryAndGetId(incomeGroup.getId(), "A Income", BigDecimal.ZERO, new BigDecimal("50.00"));
        Long bIncomeId = createCategoryAndGetId(incomeGroup.getId(), "B Income", BigDecimal.ZERO, new BigDecimal("500.00"));
        Long expenseId = createCategoryAndGetId(expenseGroup.getId(), "Rent", new BigDecimal("300.00"), BigDecimal.ZERO);

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(300.00)))
                .andExpect(jsonPath("$.transfersCreated", is(2)))
                .andExpect(jsonPath("$.remainingUnallocated", is(250.00)));

        assertThat(balanceOf(aIncomeId)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(bIncomeId)).isEqualByComparingTo("250.00");
        assertThat(balanceOf(expenseId)).isEqualByComparingTo("300.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_insufficientIncome_partiallyFundsInDisplayOrder() throws Exception {
        Long salaryId = createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("100.00"));
        // sorted alphabetically: "Groceries" is funded before "Rent"
        Long groceriesId = createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("80.00"), BigDecimal.ZERO);
        Long rentId = createCategoryAndGetId(expenseGroup.getId(), "Rent", new BigDecimal("80.00"), BigDecimal.ZERO);

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(100.00)))
                .andExpect(jsonPath("$.transfersCreated", is(2)))
                .andExpect(jsonPath("$.remainingUnallocated", is(0.00)));

        assertThat(balanceOf(salaryId)).isEqualByComparingTo("0.00");
        assertThat(balanceOf(groceriesId)).isEqualByComparingTo("80.00");
        assertThat(balanceOf(rentId)).isEqualByComparingTo("20.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_categoryAlreadyFullyFundedFromIncome_isSkipped() throws Exception {
        Long salaryId = createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("200.00"));
        Long groceriesId = createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);
        Long rentId = createCategoryAndGetId(expenseGroup.getId(), "Rent", new BigDecimal("100.00"), BigDecimal.ZERO);

        manualTransfer(salaryId, groceriesId, new BigDecimal("50.00")); // Groceries is now fully funded

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(100.00)))
                .andExpect(jsonPath("$.transfersCreated", is(1)))
                .andExpect(jsonPath("$.remainingUnallocated", is(50.00)));

        assertThat(balanceOf(groceriesId)).isEqualByComparingTo("50.00"); // unchanged by auto-allocate
        assertThat(balanceOf(rentId)).isEqualByComparingTo("100.00");
        assertThat(balanceOf(salaryId)).isEqualByComparingTo("50.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_afterTransferBackToIncome_increasesRemainingNeedAgain() throws Exception {
        Long salaryId = createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("200.00"));
        Long groceriesId = createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);

        manualTransfer(salaryId, groceriesId, new BigDecimal("50.00")); // fully funds Groceries
        manualTransfer(groceriesId, salaryId, new BigDecimal("20.00")); // partially reverses it

        assertThat(balanceOf(groceriesId)).isEqualByComparingTo("30.00");
        assertThat(balanceOf(salaryId)).isEqualByComparingTo("170.00");

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(20.00)))
                .andExpect(jsonPath("$.transfersCreated", is(1)))
                .andExpect(jsonPath("$.remainingUnallocated", is(150.00)));

        assertThat(balanceOf(groceriesId)).isEqualByComparingTo("50.00");
        assertThat(balanceOf(salaryId)).isEqualByComparingTo("150.00");
    }

    // ── No-op cases ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_noIncomeBalance_returnsZeroNoOp() throws Exception {
        createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, BigDecimal.ZERO);
        createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);

        long countBefore = transactionRepository.count();

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(0.00)))
                .andExpect(jsonPath("$.transfersCreated", is(0)))
                .andExpect(jsonPath("$.remainingUnallocated", is(0.00)));

        assertThat(transactionRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void autoAllocate_noExpenseCategories_returnsZeroNoOp() throws Exception {
        createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("300.00"));

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAllocated", is(0.00)))
                .andExpect(jsonPath("$.transfersCreated", is(0)));
    }

    // ── hasFundsToAutoAllocate (drives the button's disabled state) ─────────────

    @Test
    @WithMockUser(username = EMAIL)
    void hasFundsToAutoAllocate_returnsTrueWhenIncomeCanFundAnUnderfundedCategory() throws Exception {
        createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("100.00"));
        createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);

        Budget freshBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(categoryService.hasFundsToAutoAllocate(freshBudget)).isTrue();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void hasFundsToAutoAllocate_returnsFalseWhenNoIncomeBalance() throws Exception {
        createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, BigDecimal.ZERO);
        createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);

        Budget freshBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(categoryService.hasFundsToAutoAllocate(freshBudget)).isFalse();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void hasFundsToAutoAllocate_returnsFalseWhenAllExpenseCategoriesAreFullyFunded() throws Exception {
        Long salaryId = createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("100.00"));
        Long groceriesId = createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);
        manualTransfer(salaryId, groceriesId, new BigDecimal("50.00"));

        Budget freshBudget = budgetRepository.findById(budget.getId()).orElseThrow();
        assertThat(categoryService.hasFundsToAutoAllocate(freshBudget)).isFalse();
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "someone_else@example.com")
    void autoAllocate_forBudgetBelongingToAnotherUser_returns404() throws Exception {
        AppUser otherUser = new AppUser("someone_else@example.com", passwordEncoder.encode("somepassword"));
        appUserRepository.save(otherUser);

        createCategoryAndGetId(incomeGroup.getId(), "Salary", BigDecimal.ZERO, new BigDecimal("300.00"));
        createCategoryAndGetId(expenseGroup.getId(), "Groceries", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().isNotFound());
    }

    @Test
    void autoAllocate_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(post(autoAllocateUrl()))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
