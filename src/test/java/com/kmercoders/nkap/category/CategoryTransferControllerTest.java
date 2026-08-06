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
import com.kmercoders.nkap.transaction.Direction;
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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CategoryTransferControllerTest {

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

    private static final String EMAIL = "transfer_user@example.com";

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
                    Group g = new Group("Groceries");
                    groupRepository.save(g);
                    budget.getGroups().add(g);
                    budgetRepository.save(budget);
                    return g;
                });
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

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

    private Group createOtherGroup() throws Exception {
        mockMvc.perform(post("/budgets/%d/groups".formatted(budget.getId()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new GroupDTO("Savings"))))
                .andExpect(status().isOk());

        return groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(g -> "Savings".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Savings group not found"));
    }

    private String transferRequestJson(Long sourceCategoryId, Long targetCategoryId, Object amount) throws Exception {
        var node = objectMapper.createObjectNode();
        node.put("sourceCategoryId", sourceCategoryId);
        node.put("targetCategoryId", targetCategoryId);
        if (amount instanceof BigDecimal bd) {
            node.put("amount", bd);
        } else if (amount != null) {
            node.put("amount", amount.toString());
        } else {
            node.putNull("amount");
        }
        return objectMapper.writeValueAsString(node);
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_lessThanFullBalance_returns200AndUpdatesBalances() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("80.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), new BigDecimal("20.00"));

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("30.00"))))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.id", is(sourceId.intValue())))
                .andExpect(jsonPath("$.source.balance", is(50.00)))
                .andExpect(jsonPath("$.target.id", is(targetId.intValue())))
                .andExpect(jsonPath("$.target.balance", is(50.00)));

        assertThat(categoryRepository.findById(sourceId).orElseThrow().getBalance()).isEqualByComparingTo("50.00");
        assertThat(categoryRepository.findById(targetId).orElseThrow().getBalance()).isEqualByComparingTo("50.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_createsTwoTransferTransactionsSharingTransferId() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("80.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        // two ADJUSTMENT transactions already exist from category creation (only "Dining" got one, since
        // "Savings Goal" was created with a zero balance)
        assertThat(transactionRepository.findByBudgetId(budget.getId())).hasSize(1);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("30.00"))))
                .andExpect(status().isOk());

        List<Transaction> all = transactionRepository.findByBudgetId(budget.getId());
        assertThat(all).hasSize(3); // 1 adjustment + 2 transfer legs

        List<Transaction> transferLegs = all.stream()
                .filter(t -> t.getTransactionType() == TransactionType.TRANSFER)
                .toList();
        assertThat(transferLegs).hasSize(2);

        Transaction debitLeg = transferLegs.stream()
                .filter(t -> t.getDirection() == Direction.DEBIT)
                .findFirst().orElseThrow();
        Transaction creditLeg = transferLegs.stream()
                .filter(t -> t.getDirection() == Direction.CREDIT)
                .findFirst().orElseThrow();

        BudgetCategory sourceBc = budgetCategoryRepository.findByBudgetIdAndCategoryId(budget.getId(), sourceId).orElseThrow();
        BudgetCategory targetBc = budgetCategoryRepository.findByBudgetIdAndCategoryId(budget.getId(), targetId).orElseThrow();

        assertThat(debitLeg.getAmount()).isEqualByComparingTo("30.00");
        assertThat(debitLeg.getBudgetCategory().getId()).isEqualTo(sourceBc.getId());
        assertThat(creditLeg.getAmount()).isEqualByComparingTo("30.00");
        assertThat(creditLeg.getBudgetCategory().getId()).isEqualTo(targetBc.getId());

        assertThat(debitLeg.getTransferId()).isNotNull();
        assertThat(debitLeg.getTransferId()).isEqualTo(creditLeg.getTransferId());

        assertThat(transactionRepository.findByTransferId(debitLeg.getTransferId()))
                .extracting(Transaction::getId)
                .containsExactlyInAnyOrder(debitLeg.getId(), creditLeg.getId());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_fullAvailableBalance_returns200AndZeroesSourceBalance() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("30.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.balance", is(0.00)))
                .andExpect(jsonPath("$.target.balance", is(30.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_acrossDifferentGroups_returns200() throws Exception {
        Group otherGroup = createOtherGroup();

        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("40.00"));
        Long targetId = createCategoryAndGetId(otherGroup.getId(), "Emergency Fund", new BigDecimal("0.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("15.00"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source.balance", is(25.00)))
                .andExpect(jsonPath("$.target.balance", is(15.00)));
    }

    // ── Validation / domain failures ───────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_moreThanAvailable_returns400AndDoesNotChangeBalances() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        long countBefore = transactionRepository.count();

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("30.01"))))
                .andExpect(status().isBadRequest());

        assertThat(categoryRepository.findById(sourceId).orElseThrow().getBalance()).isEqualByComparingTo("30.00");
        assertThat(categoryRepository.findById(targetId).orElseThrow().getBalance()).isEqualByComparingTo("0.00");
        assertThat(transactionRepository.count()).isEqualTo(countBefore);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_whenSourceBalanceIsZero_returns400() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), BigDecimal.ZERO);
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("1.00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_sameSourceAndTarget_returns400() throws Exception {
        Long categoryId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(categoryId, categoryId, new BigDecimal("10.00"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withZeroAmount_returns400WithFieldError() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, BigDecimal.ZERO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withNegativeAmount_returns400WithFieldError() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("-5.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withMissingAmount_returns400WithFieldError() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, null)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withMissingSourceCategoryId_returns400WithFieldError() throws Exception {
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(null, targetId, new BigDecimal("10.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.sourceCategoryId", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withMissingTargetCategoryId_returns400WithFieldError() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, null, new BigDecimal("10.00"))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.targetCategoryId", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withUnknownTargetCategory_returns404() throws Exception {
        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, 999L, new BigDecimal("10.00"))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withUnknownSourceCategory_returns404() throws Exception {
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(999L, targetId, new BigDecimal("10.00"))))
                .andExpect(status().isNotFound());
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "someone_else@example.com")
    void transfer_forBudgetBelongingToAnotherUser_returns404() throws Exception {
        AppUser otherUser = new AppUser("someone_else@example.com", passwordEncoder.encode("somepassword"));
        appUserRepository.save(otherUser);

        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));
        Long targetId = createCategoryAndGetId(group.getId(), "Savings Goal", new BigDecimal("50.00"), BigDecimal.ZERO);

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, targetId, new BigDecimal("10.00"))))
                .andExpect(status().isNotFound());

        assertThat(categoryRepository.findById(sourceId).orElseThrow().getBalance()).isEqualByComparingTo("30.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void transfer_withCategoryBelongingToAnotherUsersBudget_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_owner@example.com", passwordEncoder.encode("somepassword"));
        appUserRepository.save(otherUser);
        Budget otherBudget = budgetService.createBudget(otherUser, Month.JANUARY, 2025);
        Group otherGroup = groupRepository.findByBudgetsId(otherBudget.getId()).stream()
                .findFirst().orElseThrow();

        Category otherCategory = new Category("Other User Category", otherGroup);
        otherCategory.setBalance(new BigDecimal("40.00"));
        categoryRepository.save(otherCategory);
        BudgetCategory otherBc = new BudgetCategory(otherBudget, otherCategory, new BigDecimal("40.00"));
        budgetCategoryRepository.save(otherBc);

        Long sourceId = createCategoryAndGetId(group.getId(), "Dining", new BigDecimal("100.00"), new BigDecimal("30.00"));

        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(sourceId, otherCategory.getId(), new BigDecimal("10.00"))))
                .andExpect(status().isNotFound());
    }

    @Test
    void transfer_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(post(transferUrl())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(transferRequestJson(1L, 2L, new BigDecimal("10.00"))))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
