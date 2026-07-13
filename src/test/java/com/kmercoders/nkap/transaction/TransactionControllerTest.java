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
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

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
        TransactionRequest req = request(new BigDecimal("49.99"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 15));
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
        TransactionRequest req = request(new BigDecimal("20.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 10));
        req.setDescription("Coffee shop");
        req.setBudgetId(budgetId);

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
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description", nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withMinimumFields_returns200() throws Exception {
        TransactionRequest req = request(new BigDecimal("100.00"), TransactionType.CREDIT, LocalDate.of(2026, Month.JANUARY, 3));
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",              notNullValue()))
                .andExpect(jsonPath("$.transactionType", is("CREDIT")))
                .andExpect(jsonPath("$.accountId",       nullValue()))
                .andExpect(jsonPath("$.categoryId",      nullValue()))
                .andExpect(jsonPath("$.budgetId",        is(budgetId.intValue())));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withoutAccount_returns200() throws Exception {
        TransactionRequest req = request(new BigDecimal("25.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 12));
        req.setCategoryId(categoryId);
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withZeroAmount_returns200() throws Exception {
        TransactionRequest req = request(BigDecimal.ZERO, TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 12));
        req.setNote("Free item with discount");
        req.setBudgetId(budgetId);

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
            TransactionRequest req = request(new BigDecimal("10.00"), type, LocalDate.of(2026, Month.JANUARY, 12));
            req.setBudgetId(budgetId);

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
        TransactionRequest req = request(new BigDecimal("75.50"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 20));
        req.setNote("Utility bill");
        req.setAccountId(accountId);
        req.setBudgetId(budgetId);

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
        TransactionRequest req = request(new BigDecimal("12.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setDescription("Bus ticket");
        req.setAccountId(accountId);
        req.setBudgetId(budgetId);

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
        TransactionRequest req = request(new BigDecimal("30.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
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
    void createTransaction_withNullBudgetId_returns400WithFieldError() throws Exception {
        TransactionRequest req = request(new BigDecimal("15.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 12));
        req.setCategoryId(categoryId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.budgetId", notNullValue()));

        assertThat(transactionRepository.count()).isZero();
    }

    // ── Create: Not found / access isolation ──────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNonExistentAccount_returns404() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.now());
        req.setAccountId(999999L);
        req.setBudgetId(budgetId);

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
        req.setBudgetId(budgetId);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createTransaction_withNonExistentCategory_returns404() throws Exception {
        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 12));
        req.setCategoryId(999999L);
        req.setBudgetId(budgetId);

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

        TransactionRequest req = request(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 15));
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

    // ── Update: Helpers ────────────────────────────────────────────────────────

    private Transaction seedTransaction(BigDecimal amount, TransactionType type, LocalDate date) {
        Budget budget = budgetRepository.findById(budgetId).orElseThrow();
        Transaction t = new Transaction(amount, date, type, null, null, null, budget);
        return transactionRepository.save(t);
    }

    // ── Update: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withAllFields_returns200AndUpdatedPayload() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("99.99"), TransactionType.CREDIT, LocalDate.of(2026, Month.JANUARY, 20));
        req.setDescription("Updated description");
        req.setNote("Updated note");
        req.setAccountId(accountId);
        req.setCategoryId(categoryId);
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",              is(existing.getId().intValue())))
                .andExpect(jsonPath("$.amount",          is(99.99)))
                .andExpect(jsonPath("$.transactionType", is("CREDIT")))
                .andExpect(jsonPath("$.transactionDate", is("2026-01-20")))
                .andExpect(jsonPath("$.description",     is("Updated description")))
                .andExpect(jsonPath("$.note",            is("Updated note")))
                .andExpect(jsonPath("$.accountId",       is(accountId.intValue())))
                .andExpect(jsonPath("$.categoryId",      is(categoryId.intValue())))
                .andExpect(jsonPath("$.budgetId",        is(budgetId.intValue())));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withMinimumFields_returns200() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 10));

        TransactionRequest req = request(new BigDecimal("25.00"), TransactionType.CREDIT, LocalDate.of(2026, Month.JANUARY, 12));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",              is(existing.getId().intValue())))
                .andExpect(jsonPath("$.amount",          is(25.00)))
                .andExpect(jsonPath("$.transactionType", is("CREDIT")))
                .andExpect(jsonPath("$.accountId",       nullValue()))
                .andExpect(jsonPath("$.categoryId",      nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_persistsChangesToDatabase() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("50.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 1));

        TransactionRequest req = request(new BigDecimal("123.45"), TransactionType.CREDIT, LocalDate.of(2026, Month.JANUARY, 28));
        req.setNote("Persisted note");
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        Transaction updated = transactionRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getAmount()).isEqualByComparingTo("123.45");
        assertThat(updated.getTransactionType()).isEqualTo(TransactionType.CREDIT);
        assertThat(updated.getTransactionDate()).isEqualTo(LocalDate.of(2026, Month.JANUARY, 28));
        assertThat(updated.getNote()).isEqualTo("Persisted note");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_canClearOptionalAccountAndCategory() throws Exception {
        Budget budget = budgetRepository.findById(budgetId).orElseThrow();
        Account account = accountRepository.findById(accountId).orElseThrow();
        BudgetCategory bc = budgetCategoryRepository.findByBudgetIdAndCategoryId(budgetId, categoryId).orElseThrow();
        Transaction existing = new Transaction(new BigDecimal("40.00"), LocalDate.of(2026, Month.JANUARY, 5), TransactionType.DEBIT, null, account, bc, budget);
        transactionRepository.save(existing);

        TransactionRequest req = request(new BigDecimal("40.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId",  nullValue()))
                .andExpect(jsonPath("$.categoryId", nullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_doesNotCreateDuplicate() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("20.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 7));

        TransactionRequest req = request(new BigDecimal("30.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 7));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(existing.getId().intValue())));

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    // ── Update: Validation failures ────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNullAmount_returns400WithFieldError() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(null, TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNegativeAmount_returns400WithFieldError() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("-5.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNullTransactionDate_returns400WithFieldError() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, null);
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.transactionDate", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNullTransactionType_returns400WithFieldError() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), null, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.transactionType", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withDescriptionTooLong_returns400WithFieldError() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setDescription("A".repeat(101));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.description", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNoteTooLong_returns400WithFieldError() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setNote("A".repeat(501));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.note", notNullValue()));
    }

    // ── Update: Budget date range validation ───────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withDateInWrongMonth_returns400() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.FEBRUARY, 1));
        req.setBudgetId(budgetId); // budget is JANUARY 2026

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withDateInWrongYear_returns400() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2025, Month.JANUARY, 5));
        req.setBudgetId(budgetId); // budget is JANUARY 2026

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── Update: Not found / access isolation ──────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNonExistentId_returns404() throws Exception {
        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", 999999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withAnotherUsersTransaction_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_update_tx@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Budget otherBudget = budgetRepository.save(new Budget(Month.MARCH, 2026, otherUser));
        Transaction otherTx = new Transaction(new BigDecimal("10.00"), LocalDate.of(2026, Month.MARCH, 1), TransactionType.DEBIT, null, null, null, otherBudget);
        transactionRepository.save(otherTx);

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", otherTx.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNonExistentAccount_returns404() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setAccountId(999999L);
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withAnotherUsersAccount_returns404() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        AppUser otherUser = new AppUser("other_acc_update@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Account otherAccount = accountRepository.save(
            new Account(AccountType.SAVINGS, "Their Account", BigDecimal.ZERO, otherUser));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setAccountId(otherAccount.getId());
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNonExistentBudget_returns404() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.now());
        req.setBudgetId(999999L);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withAnotherUsersBudget_returns404() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        AppUser otherUser = new AppUser("other_bud_update@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Budget otherBudget = budgetRepository.save(new Budget(Month.MARCH, 2026, otherUser));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.now());
        req.setBudgetId(otherBudget.getId());

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withNonExistentCategory_returns404() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setCategoryId(999999L);
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateTransaction_withCategoryNotLinkedToBudget_returns404() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        Group otherGroup = groupRepository.save(new Group("Other"));
        Category unlinked = categoryRepository.save(new Category("Unlinked", otherGroup));

        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setCategoryId(unlinked.getId());
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── Update: Security ───────────────────────────────────────────────────────

    @Test
    void updateTransaction_withoutAuthentication_returns401or302() throws Exception {
        TransactionRequest req = request(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        req.setBudgetId(budgetId);

        mockMvc.perform(put(URL + "/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Delete: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void deleteTransaction_withExistingId_returns204() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        mockMvc.perform(delete(URL + "/{id}", existing.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteTransaction_removesFromDatabase() throws Exception {
        Transaction existing = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));

        mockMvc.perform(delete(URL + "/{id}", existing.getId()))
                .andExpect(status().isNoContent());

        assertThat(transactionRepository.findById(existing.getId())).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteTransaction_doesNotAffectOtherTransactions() throws Exception {
        Transaction toDelete = seedTransaction(new BigDecimal("10.00"), TransactionType.DEBIT, LocalDate.of(2026, Month.JANUARY, 5));
        Transaction toKeep   = seedTransaction(new BigDecimal("20.00"), TransactionType.CREDIT, LocalDate.of(2026, Month.JANUARY, 6));

        mockMvc.perform(delete(URL + "/{id}", toDelete.getId()))
                .andExpect(status().isNoContent());

        assertThat(transactionRepository.findById(toKeep.getId())).isPresent();
    }

    // ── Delete: Not found / access isolation ──────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void deleteTransaction_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete(URL + "/{id}", 999999L))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteTransaction_withAnotherUsersTransaction_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_delete_tx@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Budget otherBudget = budgetRepository.save(new Budget(Month.MARCH, 2026, otherUser));
        Transaction otherTx = new Transaction(new BigDecimal("10.00"), LocalDate.of(2026, Month.MARCH, 1), TransactionType.DEBIT, null, null, null, otherBudget);
        transactionRepository.save(otherTx);

        mockMvc.perform(delete(URL + "/{id}", otherTx.getId()))
                .andExpect(status().isNotFound());

        assertThat(transactionRepository.findById(otherTx.getId())).isPresent();
    }

    // ── Delete: Security ───────────────────────────────────────────────────────

    @Test
    void deleteTransaction_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(delete(URL + "/{id}", 1L))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
