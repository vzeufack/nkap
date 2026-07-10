package com.kmercoders.nkap.transaction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.account.AccountType;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.category.BudgetCategoryRepository;
import com.kmercoders.nkap.category.Category;
import com.kmercoders.nkap.category.CategoryRepository;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupRepository;

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
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetCategoryRepository budgetCategoryRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "transaction_user@example.com";
    private static final String URL   = "/transactions";

    private Long accountId;
    private Long categoryId;
    private Long budgetId;

    @BeforeAll
    void setUp() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        groupRepository.deleteAll();
        accountRepository.deleteAll();
        appUserRepository.deleteAll();

        AppUser user = new AppUser(EMAIL, passwordEncoder.encode("somepassword"));
        appUserRepository.save(user);

        Account account = accountRepository.save(
            new Account(AccountType.CHECKING, "Test Account", new BigDecimal("1000.00"), user));
        accountId = account.getId();

        Group group = groupRepository.save(new Group("Expenses"));
        Category category = categoryRepository.save(new Category("Groceries", group));
        categoryId = category.getId();

        Budget budget = budgetRepository.save(new Budget(Month.JANUARY, 2026, user));
        budgetId = budget.getId();

        budgetCategoryRepository.save(new BudgetCategory(budget, category, BigDecimal.ZERO));
    }

    @BeforeEach
    void clearTransactions() {
        transactionRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private TransactionRequest request(BigDecimal amount, TransactionType type, LocalDate date) {
        TransactionRequest r = new TransactionRequest();
        r.setAmount(amount);
        r.setTransactionType(type);
        r.setTransactionDate(date);
        return r;
    }

    // ── Create: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withAllFields_returns200AndCorrectPayload() throws Exception {
        TransactionRequest req = request(new BigDecimal("49.99"), TransactionType.DEBIT, LocalDate.of(2026, 1, 15));
        req.setDescription("Grocery run");
        req.setNote("Weekly groceries");
        req.setAccountId(accountId);
        req.setCategoryId(categoryId);
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",              notNullValue()))
                .andExpect(jsonPath("$.amount",          is(49.99)))
                .andExpect(jsonPath("$.transactionType", is("DEBIT")))
                .andExpect(jsonPath("$.transactionDate", is("2026-01-15")))
                .andExpect(jsonPath("$.description",     is("Grocery run")))
                .andExpect(jsonPath("$.note",            is("Weekly groceries")))
                .andExpect(jsonPath("$.accountId",       is(accountId.intValue())))
                .andExpect(jsonPath("$.categoryId",      is(categoryId.intValue())))
                .andExpect(jsonPath("$.budgetId",        is(budgetId.intValue())));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withDescriptionOnly_returnsDescriptionInResponse() throws Exception {
        TransactionRequest req = request(new BigDecimal("20.00"), TransactionType.DEBIT, LocalDate.of(2026, 1, 10));
        req.setDescription("Coffee shop");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", is("Coffee shop")))
                .andExpect(jsonPath("$.note",        nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withoutDescription_returnsNullDescription() throws Exception {
        TransactionRequest req = request(new BigDecimal("30.00"), TransactionType.CREDIT, LocalDate.of(2026, Month.JANUARY, 20));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withMinimumFields_returns200() throws Exception {
        TransactionRequest req = request(new BigDecimal("100.00"), TransactionType.CREDIT, LocalDate.of(2026, 3, 1));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",              notNullValue()))
                .andExpect(jsonPath("$.transactionType", is("CREDIT")))
                .andExpect(jsonPath("$.accountId",       nullValue()))
                .andExpect(jsonPath("$.categoryId",      nullValue()))
                .andExpect(jsonPath("$.budgetId",        nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withoutAccount_returns200() throws Exception {
        TransactionRequest req = request(new BigDecimal("25.00"), TransactionType.DEBIT, LocalDate.now());
        req.setCategoryId(categoryId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withZeroAmount_returns200() throws Exception {
        TransactionRequest req = request(BigDecimal.ZERO, TransactionType.DEBIT, LocalDate.now());
        req.setNote("Free item with discount");

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(0)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_bothTransactionTypesAreAccepted() throws Exception {
        for (TransactionType type : TransactionType.values()) {
            TransactionRequest req = request(new BigDecimal("10.00"), type, LocalDate.now());

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.transactionType", is(type.name())));
        }
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_persistsToDatabase() throws Exception {
        TransactionRequest req = request(new BigDecimal("75.50"), TransactionType.DEBIT, LocalDate.of(2026, 2, 20));
        req.setNote("Utility bill");
        req.setAccountId(accountId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<Transaction> saved = transactionRepository.findByAccountId(accountId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getAmount()).isEqualByComparingTo("75.50");
        assertThat(saved.get(0).getTransactionType()).isEqualTo(TransactionType.DEBIT);
        assertThat(saved.get(0).getNote()).isEqualTo("Utility bill");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_descriptionPersistedToDatabase() throws Exception {
        TransactionRequest req = request(new BigDecimal("12.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.FEBRUARY, 5));
        req.setDescription("Bus ticket");
        req.setAccountId(accountId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        List<Transaction> saved = transactionRepository.findByAccountId(accountId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getDescription()).isEqualTo("Bus ticket");
    }

    // ── Create: Validation failures ────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNullAmount_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(null, TransactionType.DEBIT, LocalDate.now());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNegativeAmount_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(new BigDecimal("-10.00"), TransactionType.DEBIT, LocalDate.now());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNullTransactionDate_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, null);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.transactionDate", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNullTransactionType_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), null, LocalDate.now());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.transactionType", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNoteTooLong_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setNote("A".repeat(501));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.note", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withDescriptionTooLong_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setDescription("A".repeat(101));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withBudgetAndCategory_persistsBudgetCategoryLink() throws Exception {
        TransactionRequest req = request(new BigDecimal("30.00"), TransactionType.DEBIT, LocalDate.of(2026, 1, 5));
        req.setCategoryId(categoryId);
        req.setBudgetId(budgetId);

        // Response confirms the category and budget are reflected (session still open in service)
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId", is(categoryId.intValue())))
                .andExpect(jsonPath("$.budgetId",   is(budgetId.intValue())));

        List<Transaction> saved = transactionRepository.findByBudgetId(budgetId);
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getBudgetCategory()).isNotNull();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withCategoryOnlyNoBudget_categoryIdIsNullInResponse() throws Exception {
        TransactionRequest req = request(new BigDecimal("15.00"), TransactionType.DEBIT, LocalDate.now());
        req.setCategoryId(categoryId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoryId", nullValue()))
                .andExpect(jsonPath("$.budgetId",   nullValue()));
    }

    // ── Create: Not found / access isolation ──────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNonExistentAccount_returns404() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setAccountId(999999L);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withAnotherUsersAccount_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_tx@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Account otherAccount = accountRepository.save(
            new Account(AccountType.SAVINGS, "Other Account", BigDecimal.ZERO, otherUser));

        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setAccountId(otherAccount.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNonExistentCategory_returns404() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setCategoryId(999999L);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNonExistentBudget_returns404() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setBudgetId(999999L);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withCategoryAndBudgetNotLinked_returns404() throws Exception {
        Group otherGroup = groupRepository.save(new Group("Other"));
        Category unlinkedCategory = categoryRepository.save(new Category("Unlinked", otherGroup));

        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, 1, 15));
        req.setBudgetId(budgetId);
        req.setCategoryId(unlinkedCategory.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withAnotherUsersBudget_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_budget_tx@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Budget otherBudget = budgetRepository.save(new Budget(Month.MARCH, 2026, otherUser));

        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setBudgetId(otherBudget.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── Budget date range validation ───────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withDateInWrongMonth_returns400() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.FEBRUARY, 1));
        req.setBudgetId(budgetId); // budget is JANUARY 2026

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withDateInWrongYear_returns400() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2025, Month.JANUARY, 15));
        req.setBudgetId(budgetId); // budget is JANUARY 2026

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withDateOnFirstDayOfBudgetMonth_returns200() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 1));
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withDateOnLastDayOfBudgetMonth_returns200() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 31));
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    void createTransaction_withoutAuthentication_returns401or302() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
