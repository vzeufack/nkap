package com.kmercoders.nkap.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupDTO;
import com.kmercoders.nkap.group.GroupRepository;
import com.kmercoders.nkap.transaction.Transaction;
import com.kmercoders.nkap.transaction.TransactionRepository;
import com.kmercoders.nkap.transaction.TransactionRequest;
import com.kmercoders.nkap.transaction.TransactionType;
import com.kmercoders.nkap.transaction.Direction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.Comparator;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryControllerTest {

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

    private static final String EMAIL = "category_user@example.com";

    private Budget budget;
    private Group group;

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

        group = groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(g -> !g.isDefault())
                .findFirst()
                .orElseGet(() -> {
                    // if budgetService only creates the default Income group,
                    // create a non-default one explicitly
                    Group g = new Group("Groceries");
                    groupRepository.save(g);
                    budget.getGroups().add(g);
                    budgetRepository.save(budget);
                    return g;
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private String url() {
        return "/budgets/%d/groups/%d/categories".formatted(budget.getId(), group.getId());
    }

    private String url(Long categoryId) {
        return "/budgets/%d/groups/%d/categories/%d".formatted(budget.getId(), group.getId(), categoryId);
    }

    private CategoryRequest request(String name, BigDecimal allocation, BigDecimal balance) {
        CategoryRequest r = new CategoryRequest();
        r.setName(name);
        r.setAllocation(allocation);
        r.setBalance(balance);
        return r;
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withAllFields_returns200AndCorrectPayload() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",        notNullValue()))
                .andExpect(jsonPath("$.name",       is("Meat")))
                .andExpect(jsonPath("$.allocation", is(150.00)))
                .andExpect(jsonPath("$.balance",    is(50.00)))
                .andExpect(jsonPath("$.groupId",    is(group.getId().intValue())));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_persistsCategoryAndBudgetCategoryToDatabase() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<Category> categories = categoryRepository.findByGroupId(group.getId());
        assertThat(categories).hasSize(1);
        assertThat(categories.get(0).getName()).isEqualTo("Meat");
        assertThat(categories.get(0).getBalance()).isEqualByComparingTo("50.00");

        List<BudgetCategory> budgetCategories = budgetCategoryRepository.findByBudgetId(budget.getId());
        assertThat(budgetCategories).hasSize(1);
        assertThat(budgetCategories.get(0).getAllocation()).isEqualByComparingTo("150.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withoutBalance_defaultsToZero() throws Exception {
        CategoryRequest req = request("Vegetables", new BigDecimal("80.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withZeroAllocation_returns200() throws Exception {
        CategoryRequest req = request("Snacks", BigDecimal.ZERO, null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocation", is(0)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_allowsDuplicateNames() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── Validation failures ────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withBlankName_returns400WithFieldError() throws Exception {
        CategoryRequest req = request("", new BigDecimal("100.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withNullAllocation_returns200() throws Exception {
        CategoryRequest req = request("Meat", null, null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocation", is(0)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withNegativeAllocation_returns400WithFieldError() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("-10.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allocation", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withNegativeBalance_returns200() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), new BigDecimal("-5.00"));

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(-5.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withNameExceedingMaxLength_returns400WithFieldError() throws Exception {
        CategoryRequest req = request("A".repeat(101), new BigDecimal("100.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    // ── Domain failures ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withUnknownBudget_returns400() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), null);
        String badUrl = "/budgets/999/groups/%d/categories".formatted(group.getId());

        mockMvc.perform(post(badUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withUnknownGroup_returns400() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), null);
        String badUrl = "/budgets/%d/groups/999/categories".formatted(budget.getId());

        mockMvc.perform(post(badUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withGroupNotBelongingToBudget_returns403() throws Exception {
        Group orphanGroup = new Group("Orphan");
        groupRepository.save(orphanGroup);

        CategoryRequest req = request("Meat", new BigDecimal("100.00"), null);
        String badUrl = "/budgets/%d/groups/%d/categories".formatted(budget.getId(), orphanGroup.getId());

        mockMvc.perform(post(badUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden());
    }

    // ── Create: Adjustment transaction ────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withPositiveBalance_createsCreditAdjustmentTransaction() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));

        BudgetCategory bc = budgetCategoryRepository
                .findByBudgetIdAndCategoryId(budget.getId(), categoryId).orElseThrow();
        List<Transaction> transactions = transactionRepository.findByBudgetId(budget.getId());
        assertThat(transactions).hasSize(1);

        Transaction adjustment = transactions.get(0);
        assertThat(adjustment.getBudgetCategory().getId()).isEqualTo(bc.getId());
        assertThat(adjustment.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(adjustment.getDirection()).isEqualTo(Direction.CREDIT);
        assertThat(adjustment.getAmount()).isEqualByComparingTo("50.00");
        assertThat(adjustment.getAccount()).isNull();
        assertThat(adjustment.getDescription()).isNotBlank();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withNegativeBalance_createsDebitAdjustmentTransaction() throws Exception {
        createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("-5.00"));

        List<Transaction> transactions = transactionRepository.findByBudgetId(budget.getId());
        assertThat(transactions).hasSize(1);

        Transaction adjustment = transactions.get(0);
        assertThat(adjustment.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(adjustment.getDirection()).isEqualTo(Direction.DEBIT);
        assertThat(adjustment.getAmount()).isEqualByComparingTo("5.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withZeroBalance_doesNotCreateAdjustmentTransaction() throws Exception {
        createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        assertThat(transactionRepository.findByBudgetId(budget.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_withNullBalance_doesNotCreateAdjustmentTransaction() throws Exception {
        createCategoryAndGetId("Meat", new BigDecimal("150.00"), null);

        assertThat(transactionRepository.findByBudgetId(budget.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createCategory_adjustmentTransactionDate_fallsWithinBudgetMonth() throws Exception {
        // budget is January 2025, which is in the past relative to "today" in this test run,
        // so the adjustment transaction should be dated the last day of the budget month.
        createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));

        List<Transaction> transactions = transactionRepository.findByBudgetId(budget.getId());
        assertThat(transactions).hasSize(1);
        assertThat(transactions.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2025, 1, 31));
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    void createCategory_withoutAuthentication_returns401or302() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Update: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withAllFields_returns200AndCorrectPayload() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("Fish", new BigDecimal("200.00"), new BigDecimal("75.00"));

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",         is(categoryId.intValue())))
                .andExpect(jsonPath("$.name",        is("Fish")))
                .andExpect(jsonPath("$.allocation",  is(200.00)))
                .andExpect(jsonPath("$.balance",     is(75.00)))
                .andExpect(jsonPath("$.groupId",     is(group.getId().intValue())));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_persistsChangesToDatabase() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("Fish", new BigDecimal("200.00"), new BigDecimal("75.00"));

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        Category updated = categoryRepository.findById(categoryId).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Fish");
        assertThat(updated.getBalance()).isEqualByComparingTo("75.00");

        List<BudgetCategory> budgetCategories = budgetCategoryRepository.findByBudgetId(budget.getId());
        assertThat(budgetCategories).hasSize(1);
        assertThat(budgetCategories.get(0).getAllocation()).isEqualByComparingTo("200.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withNullBalance_treatsBalanceAsZero() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("Meat", new BigDecimal("200.00"), null);

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withNullAllocation_treatsAllocationAsZero() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), null);
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("Meat", null, null);

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allocation", is(0)));
    }

    // ── Update: Adjustment transaction ────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_increasingBalance_createsCreditAdjustmentTransactionForDelta() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        CategoryRequest updateReq = request("Meat", new BigDecimal("150.00"), new BigDecimal("75.00"));
        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(75.00)));

        List<Transaction> transactions = transactionRepository.findByBudgetId(budget.getId());
        assertThat(transactions).hasSize(1);

        Transaction adjustment = transactions.get(0);
        assertThat(adjustment.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(adjustment.getDirection()).isEqualTo(Direction.CREDIT);
        assertThat(adjustment.getAmount()).isEqualByComparingTo("75.00");
        assertThat(adjustment.getDescription()).isNotBlank();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_decreasingBalance_createsDebitAdjustmentTransactionForDelta() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("100.00"));

        CategoryRequest updateReq = request("Meat", new BigDecimal("150.00"), new BigDecimal("40.00"));
        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(40.00)));

        List<Transaction> transactions = transactionRepository.findByBudgetId(budget.getId());
        // one from the non-zero initial balance at creation, one from this update
        assertThat(transactions).hasSize(2);

        Transaction updateAdjustment = transactions.stream()
                .max(Comparator.comparing(Transaction::getId))
                .orElseThrow();
        assertThat(updateAdjustment.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(updateAdjustment.getDirection()).isEqualTo(Direction.DEBIT);
        assertThat(updateAdjustment.getAmount()).isEqualByComparingTo("60.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withUnchangedBalance_doesNotCreateAdjustmentTransaction() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));

        CategoryRequest updateReq = request("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));
        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk());

        // only the adjustment transaction created at category creation time
        assertThat(transactionRepository.findByBudgetId(budget.getId())).hasSize(1);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withNullBalance_treatsBalanceAsZeroAndCreatesDebitAdjustmentTransaction() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("50.00"));

        CategoryRequest updateReq = request("Meat", new BigDecimal("150.00"), null);
        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0.00)));

        List<Transaction> transactions = transactionRepository.findByBudgetId(budget.getId());
        // one from the non-zero initial balance at creation, one from this update
        assertThat(transactions).hasSize(2);

        Transaction updateAdjustment = transactions.stream()
                .max(Comparator.comparing(Transaction::getId))
                .orElseThrow();
        assertThat(updateAdjustment.getTransactionType()).isEqualTo(TransactionType.ADJUSTMENT);
        assertThat(updateAdjustment.getDirection()).isEqualTo(Direction.DEBIT);
        assertThat(updateAdjustment.getAmount()).isEqualByComparingTo("50.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withNullBalanceAndAlreadyZeroBalance_doesNotCreateAdjustmentTransaction() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        CategoryRequest updateReq = request("Meat", new BigDecimal("150.00"), null);
        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0.00)));

        assertThat(transactionRepository.findByBudgetId(budget.getId())).isEmpty();
    }

    // ── Update: Validation failures ────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withBlankName_returns400WithFieldError() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), null);
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("", new BigDecimal("150.00"), null);

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withNegativeAllocation_returns400WithFieldError() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), null);
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("Meat", new BigDecimal("-10.00"), null);

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allocation", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withNameExceedingMaxLength_returns400WithFieldError() throws Exception {
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), null);
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        CategoryRequest updateReq = request("A".repeat(101), new BigDecimal("150.00"), null);

        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    // ── Update: Domain failures ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withUnknownCategoryId_returns400() throws Exception {
        CategoryRequest updateReq = request("Fish", new BigDecimal("200.00"), null);

        mockMvc.perform(put(url(999L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateCategory_withCategoryNotBelongingToGroup_returns400() throws Exception {
        mockMvc.perform(post("/budgets/%d/groups".formatted(budget.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GroupDTO("Other"))))
                .andExpect(status().isOk());

        Group otherGroup = groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(g -> "Other".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Other group not found"));

        // Create a category under otherGroup
        CategoryRequest createReq = request("Meat", new BigDecimal("150.00"), null);
        String response = mockMvc.perform(post("/budgets/%d/groups/%d/categories"
                                .formatted(budget.getId(), otherGroup.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        Long categoryId = objectMapper.readTree(response).get("id").asLong();

        // Try to update it via group (wrong group for this category)
        CategoryRequest updateReq = request("Fish", new BigDecimal("200.00"), null);
        mockMvc.perform(put(url(categoryId))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    // ── Update: Security ───────────────────────────────────────────────────────

    @Test
    void updateCategory_withoutAuthentication_returns401or302() throws Exception {
        CategoryRequest updateReq = request("Fish", new BigDecimal("200.00"), null);

        mockMvc.perform(put(url(1L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Delete: Happy path ─────────────────────────────────────────────────────

    private Long createCategoryAndGetId(String name, BigDecimal allocation, BigDecimal balance) throws Exception {
        CategoryRequest createReq = request(name, allocation, balance);
        String response = mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        return objectMapper.readTree(response).get("id").asLong();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteCategory_withZeroBalanceAndNoTransactions_returns204() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        mockMvc.perform(delete(url(categoryId)))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteCategory_whenLastReferencingBudgetCategory_alsoDeletesCategory() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        mockMvc.perform(delete(url(categoryId)))
                .andExpect(status().isNoContent());

        assertThat(budgetCategoryRepository.findByBudgetId(budget.getId())).isEmpty();
        assertThat(categoryRepository.findById(categoryId)).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteCategory_whenOtherBudgetCategoryStillReferencesCategory_keepsCategory() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        // createBudget rolls forward groups/categories from the prior budget, so `otherBudget`
        // ends up with its own BudgetCategory row referencing the same Category.
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Budget otherBudget = budgetService.createBudget(user, Month.FEBRUARY, 2025);
        assertThat(budgetCategoryRepository.findByBudgetIdAndCategoryId(otherBudget.getId(), categoryId)).isPresent();

        mockMvc.perform(delete(url(categoryId)))
                .andExpect(status().isNoContent());

        assertThat(budgetCategoryRepository.findByBudgetId(budget.getId())).isEmpty();
        assertThat(categoryRepository.findById(categoryId)).isPresent();
        assertThat(budgetCategoryRepository.findByBudgetId(otherBudget.getId())).hasSize(1);
    }

    // ── Delete: Domain failures ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void deleteCategory_withNonZeroBalance_returns400AndDoesNotDelete() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), new BigDecimal("25.00"));

        mockMvc.perform(delete(url(categoryId)))
                .andExpect(status().isBadRequest());

        assertThat(categoryRepository.findById(categoryId)).isPresent();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteCategory_withLinkedTransaction_returns400AndDoesNotDelete() throws Exception {
        Long categoryId = createCategoryAndGetId("Meat", new BigDecimal("150.00"), BigDecimal.ZERO);

        TransactionRequest txReq = new TransactionRequest();
        txReq.setAmount(new BigDecimal("10.00"));
        txReq.setTransactionDate(LocalDate.of(2025, 1, 15));
        txReq.setDirection(Direction.DEBIT);
        txReq.setCategoryId(categoryId);
        txReq.setBudgetId(budget.getId());

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txReq)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(url(categoryId)))
                .andExpect(status().isBadRequest());

        assertThat(categoryRepository.findById(categoryId)).isPresent();
        assertThat(budgetCategoryRepository.findByBudgetIdAndCategoryId(budget.getId(), categoryId)).isPresent();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteCategory_withUnknownCategoryId_returns404() throws Exception {
        mockMvc.perform(delete(url(999L)))
                .andExpect(status().isNotFound());
    }

    // ── Delete: Security ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "someone_else@example.com")
    void deleteCategory_forBudgetBelongingToAnotherUser_returns404() throws Exception {
        AppUser otherUser = new AppUser("someone_else@example.com", passwordEncoder.encode("somepassword"));
        appUserRepository.save(otherUser);

        // Category belongs to `budget`, which is owned by EMAIL, not otherUser.
        Category category = new Category("Meat", group);
        category.setBalance(BigDecimal.ZERO);
        categoryRepository.save(category);
        BudgetCategory bc = new BudgetCategory(budget, category, new BigDecimal("150.00"));
        budgetCategoryRepository.save(bc);

        mockMvc.perform(delete(url(category.getId())))
                .andExpect(status().isNotFound());

        assertThat(categoryRepository.findById(category.getId())).isPresent();
    }

    @Test
    void deleteCategory_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(delete(url(1L)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}