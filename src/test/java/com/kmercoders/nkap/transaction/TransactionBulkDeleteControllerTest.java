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
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.Month;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** POST /transactions/bulk-delete — multi-select delete on the transactions page. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionBulkDeleteControllerTest {

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

    private static final String EMAIL       = "tx_bulk_delete_user@example.com";
    private static final String OTHER_EMAIL = "tx_bulk_delete_other@example.com";
    private static final String URL         = "/transactions/bulk-delete";
    private static final LocalDate DATE     = LocalDate.of(2026, Month.JANUARY, 10);

    private Account account;
    private Category category;
    private BudgetCategory budgetCategory;
    private Budget budget;
    private Budget otherBudget;

    @BeforeAll
    void setUp() {
        transactionRepository.deleteAll();
        budgetCategoryRepository.deleteAll();
        budgetRepository.deleteAll();
        categoryRepository.deleteAll();
        groupRepository.deleteAll();
        accountRepository.deleteAll();
        appUserRepository.deleteAll();

        AppUser user  = appUserRepository.save(new AppUser(EMAIL, passwordEncoder.encode("somepassword")));
        AppUser other = appUserRepository.save(new AppUser(OTHER_EMAIL, passwordEncoder.encode("somepassword")));

        account  = accountRepository.save(new Account(AccountType.CHECKING, "Checking", BigDecimal.ZERO, user));
        Group group = groupRepository.save(new Group("Living Expenses"));
        category = categoryRepository.save(new Category("Groceries", group));

        budget      = budgetRepository.save(new Budget(Month.JANUARY, 2026, user));
        otherBudget = budgetRepository.save(new Budget(Month.JANUARY, 2026, other));
        budgetCategory = budgetCategoryRepository.save(new BudgetCategory(budget, category, BigDecimal.ZERO));
    }

    @BeforeEach
    void reset() {
        transactionRepository.deleteAll();
        account.setBalance(BigDecimal.ZERO);
        account = accountRepository.save(account);
        category.setBalance(BigDecimal.ZERO);
        category = categoryRepository.save(category);
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /** Saves a transaction and applies its balance effect, as the app would have. */
    private Transaction saveApplied(String amount, Direction direction, TransactionType type, Budget txBudget) {
        Transaction t = transactionRepository.save(new Transaction(new BigDecimal(amount), DATE, direction, type, null,
            txBudget == budget ? account : null, txBudget == budget ? budgetCategory : null, txBudget));
        if (txBudget == budget) {
            BigDecimal signed = direction == Direction.DEBIT ? new BigDecimal(amount).negate() : new BigDecimal(amount);
            account.setBalance(account.getBalance().add(signed));
            account = accountRepository.save(account);
            category.setBalance(category.getBalance().add(signed));
            category = categoryRepository.save(category);
        }
        return t;
    }

    private ResultActions bulkDelete(Object... ids) throws Exception {
        return mockMvc.perform(post(URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("ids", List.of(ids)))));
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_deletesSelectedAndRevertsBalances() throws Exception {
        Transaction debit  = saveApplied("40.00", Direction.DEBIT,  TransactionType.STANDARD, budget);
        Transaction credit = saveApplied("15.00", Direction.CREDIT, TransactionType.STANDARD, budget);
        Transaction kept   = saveApplied("5.00",  Direction.DEBIT,  TransactionType.STANDARD, budget);

        bulkDelete(debit.getId(), credit.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", is(2)));

        assertThat(transactionRepository.findAll()).extracting(Transaction::getId).containsExactly(kept.getId());
        // Only the kept −5.00 remains applied.
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("-5.00");
        assertThat(categoryRepository.findById(category.getId()).orElseThrow().getBalance()).isEqualByComparingTo("-5.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_ignoresDuplicateIds() throws Exception {
        Transaction t = saveApplied("10.00", Direction.DEBIT, TransactionType.STANDARD, budget);

        bulkDelete(t.getId(), t.getId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deleted", is(1)));

        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("0");
    }

    // ── All or nothing ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_withAnotherUsersTransaction_returns404AndDeletesNothing() throws Exception {
        Transaction mine   = saveApplied("10.00", Direction.DEBIT, TransactionType.STANDARD, budget);
        Transaction theirs = saveApplied("10.00", Direction.DEBIT, TransactionType.STANDARD, otherBudget);

        bulkDelete(mine.getId(), theirs.getId()).andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isEqualTo(2);
        assertThat(accountRepository.findById(account.getId()).orElseThrow().getBalance()).isEqualByComparingTo("-10.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_withNonExistentId_returns404AndDeletesNothing() throws Exception {
        Transaction mine = saveApplied("10.00", Direction.DEBIT, TransactionType.STANDARD, budget);

        bulkDelete(mine.getId(), 999_999L).andExpect(status().isNotFound());

        assertThat(transactionRepository.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_withNonStandardTransaction_returns400AndDeletesNothing() throws Exception {
        Transaction standard = saveApplied("10.00", Direction.DEBIT,  TransactionType.STANDARD, budget);
        Transaction transfer = saveApplied("10.00", Direction.CREDIT, TransactionType.TRANSFER, budget);

        bulkDelete(standard.getId(), transfer.getId()).andExpect(status().isBadRequest());

        assertThat(transactionRepository.count()).isEqualTo(2);
    }

    // ── Validation ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_withNoIds_returns400WithFieldError() throws Exception {
        bulkDelete()
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ids", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void bulkDelete_withMissingIds_returns400WithFieldError() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.ids", notNullValue()));
    }

    // ── Unauthenticated ────────────────────────────────────────────────────────

    @Test
    void bulkDelete_withoutAuthentication_returns401or302() throws Exception {
        bulkDelete(1L).andExpect(status().is(anyOf(is(401), is(302))));
    }
}
