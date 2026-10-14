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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * POST /transactions/by-date and PUT /transactions/{id}/by-date — the transactions page's add and
 * edit, where the budget is derived from the transaction date (created if missing) and unlinked
 * categories are linked again.
 *
 * Unlike the other controller tests, budgets/groups/categories are reseeded before every test:
 * the endpoint itself creates budgets and links, so state from one test would leak into the next.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionByDateControllerTest {

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

    private static final String EMAIL       = "tx_by_date_user@example.com";
    private static final String OTHER_EMAIL = "tx_by_date_other@example.com";
    private static final String URL         = "/transactions/by-date";
    private static final String UPDATE_URL  = "/transactions/{id}/by-date";

    private AppUser user;
    private AppUser otherUser;

    private Account account;
    private Account otherAccount;
    private Group group;
    private Category category;
    private Budget januaryBudget;
    private Category otherCategory;

    @BeforeAll
    void createUsers() {
        clearAll();
        appUserRepository.deleteAll();
        user      = appUserRepository.save(new AppUser(EMAIL, passwordEncoder.encode("somepassword")));
        otherUser = appUserRepository.save(new AppUser(OTHER_EMAIL, passwordEncoder.encode("somepassword")));
    }

    @BeforeEach
    void seed() {
        clearAll();

        account      = accountRepository.save(new Account(AccountType.CHECKING, "Checking", new BigDecimal("1000.00"), user));
        otherAccount = accountRepository.save(new Account(AccountType.CHECKING, "Theirs", new BigDecimal("1000.00"), otherUser));

        group    = groupRepository.save(new Group("Living Expenses"));
        category = categoryRepository.save(new Category("Groceries", group));

        januaryBudget = new Budget(Month.JANUARY, 2026, user);
        januaryBudget.addGroup(group);
        januaryBudget = budgetRepository.save(januaryBudget);
        budgetCategoryRepository.save(new BudgetCategory(januaryBudget, category, new BigDecimal("300.00")));

        Group otherGroup = groupRepository.save(new Group("Their Expenses"));
        otherCategory = categoryRepository.save(new Category("Their Groceries", otherGroup));
        Budget otherBudget = new Budget(Month.JANUARY, 2026, otherUser);
        otherBudget.addGroup(otherGroup);
        otherBudget = budgetRepository.save(otherBudget);
        budgetCategoryRepository.save(new BudgetCategory(otherBudget, otherCategory, BigDecimal.ZERO));
    }

    private void clearAll() {
        transactionRepository.deleteAll();
        budgetCategoryRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        groupRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private TransactionDetailsRequest request(String amount, LocalDate date) {
        TransactionDetailsRequest r = new TransactionDetailsRequest();
        r.setAmount(new BigDecimal(amount));
        r.setDirection(Direction.DEBIT);
        r.setTransactionDate(date);
        return r;
    }

    private void postOk(TransactionDetailsRequest req) throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());
    }

    private Budget budgetFor(Month month, int year) {
        return budgetRepository.findByAppUserIdAndMonthAndYear(user.getId(), month, year).orElseThrow();
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withExistingBudget_attachesToThatBudgetAndUpdatesBalances() throws Exception {
        TransactionDetailsRequest req = request("20.00", LocalDate.of(2026, Month.JANUARY, 15));
        req.setDescription("Corner Market");
        req.setNote("Weekly shop");
        req.setAccountId(account.getId());
        req.setCategoryId(category.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId",        is(januaryBudget.getId().intValue())))
                .andExpect(jsonPath("$.categoryId",      is(category.getId().intValue())))
                .andExpect(jsonPath("$.accountId",       is(account.getId().intValue())))
                .andExpect(jsonPath("$.transactionType", is("STANDARD")))
                .andExpect(jsonPath("$.description",     is("Corner Market")))
                .andExpect(jsonPath("$.note",            is("Weekly shop")));

        assertThat(budgetRepository.findByAppUserId(user.getId())).hasSize(1);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("980.00");
        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("-20.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withNoBudgetForMonth_createsBudgetAndAttachesTransaction() throws Exception {
        postOk(request("15.00", LocalDate.of(2026, Month.MARCH, 10)));

        Budget march = budgetFor(Month.MARCH, 2026);
        List<Transaction> saved = transactionRepository.findByBudgetId(march.getId());
        assertThat(saved).hasSize(1);
        assertThat(saved.get(0).getTransactionDate()).isEqualTo(LocalDate.of(2026, Month.MARCH, 10));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withCategoryUnlinkedFromBudget_relinksCategoryWithZeroAllocation() throws Exception {
        // February has the group but the category was unlinked from it.
        Budget february = new Budget(Month.FEBRUARY, 2026, user);
        february.addGroup(group);
        february = budgetRepository.save(february);

        TransactionDetailsRequest req = request("12.00", LocalDate.of(2026, Month.FEBRUARY, 3));
        req.setCategoryId(category.getId());
        postOk(req);

        BudgetCategory relinked = budgetCategoryRepository
            .findByBudgetIdAndCategoryId(february.getId(), category.getId()).orElseThrow();
        assertThat(relinked.getAllocation()).isEqualByComparingTo("0");
        assertThat(transactionRepository.findByBudgetId(february.getId()).get(0).getBudgetCategory().getId())
            .isEqualTo(relinked.getId());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withGroupUnlinkedFromBudget_relinksGroupAndCategory() throws Exception {
        // February exists but neither the group nor its category is linked to it.
        Budget february = budgetRepository.save(new Budget(Month.FEBRUARY, 2026, user));

        TransactionDetailsRequest req = request("12.00", LocalDate.of(2026, Month.FEBRUARY, 3));
        req.setCategoryId(category.getId());
        postOk(req);

        assertThat(groupRepository.existsByIdAndBudgetsId(group.getId(), february.getId())).isTrue();
        assertThat(budgetCategoryRepository.findByBudgetIdAndCategoryId(february.getId(), category.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withNewBudgetAndCategory_linksCategoryToCreatedBudget() throws Exception {
        TransactionDetailsRequest req = request("9.99", LocalDate.of(2026, Month.APRIL, 20));
        req.setCategoryId(category.getId());
        postOk(req);

        Budget april = budgetFor(Month.APRIL, 2026);
        assertThat(budgetCategoryRepository.findByBudgetIdAndCategoryId(april.getId(), category.getId())).isPresent();
        assertThat(transactionRepository.findByBudgetId(april.getId())).hasSize(1);
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withMissingRequiredFields_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount",          notNullValue()))
                .andExpect(jsonPath("$.transactionDate", notNullValue()))
                .andExpect(jsonPath("$.direction",       notNullValue()))
                .andExpect(jsonPath("$.budgetId").doesNotExist());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withNegativeAmount_returns400() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("-5.00", LocalDate.of(2026, Month.JANUARY, 2)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount", notNullValue()));
    }

    // ── Isolation ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withAnotherUsersCategory_returns404AndCreatesNoBudget() throws Exception {
        TransactionDetailsRequest req = request("10.00", LocalDate.of(2026, Month.MAY, 1));
        req.setCategoryId(otherCategory.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());

        assertThat(budgetRepository.findByAppUserIdAndMonthAndYear(user.getId(), Month.MAY, 2026)).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withAnotherUsersAccount_returns404AndCreatesNoBudget() throws Exception {
        TransactionDetailsRequest req = request("10.00", LocalDate.of(2026, Month.MAY, 1));
        req.setAccountId(otherAccount.getId());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());

        assertThat(budgetRepository.findByAppUserIdAndMonthAndYear(user.getId(), Month.MAY, 2026)).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createForDate_withNonExistentCategory_returns404() throws Exception {
        TransactionDetailsRequest req = request("10.00", LocalDate.of(2026, Month.JANUARY, 5));
        req.setCategoryId(999_999L);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());
    }

    // ── Unauthenticated ────────────────────────────────────────────────────────

    @Test
    void createForDate_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("10.00", LocalDate.of(2026, Month.JANUARY, 5)))))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Update ─────────────────────────────────────────────────────────────────

    /** A $20 debit in January on the account and category, with balances already applied. */
    private Transaction seedJanuaryDebit() {
        BudgetCategory januaryGroceries = budgetCategoryRepository
            .findByBudgetIdAndCategoryId(januaryBudget.getId(), category.getId()).orElseThrow();
        Transaction t = new Transaction(new BigDecimal("20.00"), LocalDate.of(2026, Month.JANUARY, 10), Direction.DEBIT,
            TransactionType.STANDARD, null, account, januaryGroceries, januaryBudget);
        t = transactionRepository.save(t);

        account.setBalance(new BigDecimal("980.00"));
        accountRepository.save(account);
        category.setBalance(new BigDecimal("-20.00"));
        categoryRepository.save(category);
        return t;
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateForDate_movingToMonthWithoutBudget_createsBudgetAndRebalances() throws Exception {
        Transaction existing = seedJanuaryDebit();

        TransactionDetailsRequest req = request("50.00", LocalDate.of(2026, Month.JUNE, 2));
        req.setDescription("Moved");
        req.setAccountId(account.getId());
        req.setCategoryId(category.getId());

        mockMvc.perform(put(UPDATE_URL, existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",              is(existing.getId().intValue())))
                .andExpect(jsonPath("$.transactionDate", is("2026-06-02")))
                .andExpect(jsonPath("$.description",     is("Moved")));

        Budget june = budgetFor(Month.JUNE, 2026);
        Transaction updated = transactionRepository.findById(existing.getId()).orElseThrow();
        assertThat(updated.getBudget().getId()).isEqualTo(june.getId());
        assertThat(budgetCategoryRepository.findByBudgetIdAndCategoryId(june.getId(), category.getId())).isPresent();
        // Old -20 reverted, new -50 applied.
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("950.00");
        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("-50.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateForDate_withCategoryUnlinkedFromBudget_relinksCategory() throws Exception {
        Transaction existing = seedJanuaryDebit();
        Budget february = budgetRepository.save(new Budget(Month.FEBRUARY, 2026, user));

        TransactionDetailsRequest req = request("20.00", LocalDate.of(2026, Month.FEBRUARY, 14));
        req.setCategoryId(category.getId());

        mockMvc.perform(put(UPDATE_URL, existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.budgetId", is(february.getId().intValue())));

        assertThat(groupRepository.existsByIdAndBudgetsId(group.getId(), february.getId())).isTrue();
        assertThat(budgetCategoryRepository.findByBudgetIdAndCategoryId(february.getId(), category.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateForDate_clearingAccountAndCategory_revertsTheirBalances() throws Exception {
        Transaction existing = seedJanuaryDebit();

        mockMvc.perform(put(UPDATE_URL, existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("20.00", LocalDate.of(2026, Month.JANUARY, 10)))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId").doesNotExist())
                .andExpect(jsonPath("$.categoryId").doesNotExist());

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("1000.00");
        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getBalance())
            .isEqualByComparingTo("0");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateForDate_withMissingRequiredFields_returns400WithFieldErrors() throws Exception {
        Transaction existing = seedJanuaryDebit();

        mockMvc.perform(put(UPDATE_URL, existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.amount",          notNullValue()))
                .andExpect(jsonPath("$.transactionDate", notNullValue()))
                .andExpect(jsonPath("$.direction",       notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateForDate_withNonExistentTransaction_returns404() throws Exception {
        mockMvc.perform(put(UPDATE_URL, 999_999L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("10.00", LocalDate.of(2026, Month.JANUARY, 5)))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = OTHER_EMAIL)
    void updateForDate_withAnotherUsersTransaction_returns404AndCreatesNoBudget() throws Exception {
        Transaction existing = seedJanuaryDebit();

        mockMvc.perform(put(UPDATE_URL, existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("10.00", LocalDate.of(2026, Month.MAY, 5)))))
                .andExpect(status().isNotFound());

        assertThat(budgetRepository.findByAppUserIdAndMonthAndYear(otherUser.getId(), Month.MAY, 2026)).isEmpty();
        assertThat(transactionRepository.findById(existing.getId()).orElseThrow().getAmount())
            .isEqualByComparingTo("20.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateForDate_withAnotherUsersCategory_returns404AndLeavesTransactionUnchanged() throws Exception {
        Transaction existing = seedJanuaryDebit();
        TransactionDetailsRequest req = request("99.00", LocalDate.of(2026, Month.MAY, 5));
        req.setCategoryId(otherCategory.getId());

        mockMvc.perform(put(UPDATE_URL, existing.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isNotFound());

        assertThat(budgetRepository.findByAppUserIdAndMonthAndYear(user.getId(), Month.MAY, 2026)).isEmpty();
        assertThat(transactionRepository.findById(existing.getId()).orElseThrow().getAmount())
            .isEqualByComparingTo("20.00");
    }

    @Test
    void updateForDate_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(put(UPDATE_URL, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request("10.00", LocalDate.of(2026, Month.JANUARY, 5)))))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
