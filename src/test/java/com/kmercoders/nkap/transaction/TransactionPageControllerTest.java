package com.kmercoders.nkap.transaction;

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
import org.springframework.data.domain.Page;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class TransactionPageControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private GroupRepository groupRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetCategoryRepository budgetCategoryRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL       = "tx_page_user@example.com";
    private static final String OTHER_EMAIL = "tx_page_other@example.com";
    private static final String URL         = "/transactions";

    private final YearMonth currentMonth  = YearMonth.now();
    private final YearMonth previousMonth = currentMonth.minusMonths(1);

    private Account account;
    private Budget currentBudget;
    private Budget previousBudget;
    private Budget otherUserBudget;
    private BudgetCategory budgetCategory;

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

        account = accountRepository.save(
            new Account(AccountType.CHECKING, "Everyday Checking", new BigDecimal("1000.00"), user));

        Group group = groupRepository.save(new Group("Living Expenses"));
        Category category = categoryRepository.save(new Category("Groceries", group));

        currentBudget   = budgetRepository.save(new Budget(currentMonth.getMonth(), currentMonth.getYear(), user));
        previousBudget  = budgetRepository.save(new Budget(previousMonth.getMonth(), previousMonth.getYear(), user));
        otherUserBudget = budgetRepository.save(new Budget(currentMonth.getMonth(), currentMonth.getYear(), other));

        budgetCategory = budgetCategoryRepository.save(new BudgetCategory(currentBudget, category, BigDecimal.ZERO));
    }

    @BeforeEach
    void clearTransactions() {
        transactionRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private Transaction save(String amount, Direction direction, TransactionType type, Budget budget, LocalDate date,
                             String description) {
        Transaction t = new Transaction(new BigDecimal(amount), date, direction, type, null, null, null, budget);
        t.setDescription(description);
        return transactionRepository.save(t);
    }

    private Transaction saveStandard(int day, String description) {
        return save("10.00", Direction.DEBIT, TransactionType.STANDARD, currentBudget, currentMonth.atDay(day), description);
    }

    @SuppressWarnings("unchecked")
    private Page<TransactionListItemDTO> pageFrom(MvcResult result) {
        return (Page<TransactionListItemDTO>) result.getModelAndView().getModel().get("transactionsPage");
    }

    private List<String> descriptions(Page<TransactionListItemDTO> page) {
        return page.getContent().stream().map(TransactionListItemDTO::getDescription).toList();
    }

    // ── Happy path ─────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_returnsOnlyStandardTransactionsInCurrentMonth() throws Exception {
        saveStandard(5, "standard-current");
        save("10.00", Direction.DEBIT, TransactionType.ADJUSTMENT, currentBudget, currentMonth.atDay(5), "adjustment");
        save("10.00", Direction.DEBIT, TransactionType.TRANSFER, currentBudget, currentMonth.atDay(5), "transfer");
        save("10.00", Direction.DEBIT, TransactionType.STANDARD, previousBudget, previousMonth.atDay(5), "standard-previous");

        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(view().name("transactions"))
                .andReturn();

        assertThat(descriptions(pageFrom(result))).containsExactly("standard-current");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_sortsNewestFirst() throws Exception {
        saveStandard(3, "day-3");
        saveStandard(9, "day-9");
        saveStandard(1, "day-1");

        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(descriptions(pageFrom(result))).containsExactly("day-9", "day-3", "day-1");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_paginatesTenPerPage() throws Exception {
        for (int day = 1; day <= 12; day++) {
            saveStandard(day, "day-" + day);
        }

        Page<TransactionListItemDTO> firstPage = pageFrom(mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getTotalElements()).isEqualTo(12);
        assertThat(firstPage.getTotalPages()).isEqualTo(2);
        assertThat(firstPage.getContent().get(0).getDescription()).isEqualTo("day-12");

        Page<TransactionListItemDTO> secondPage = pageFrom(mockMvc.perform(get(URL).param("page", "1"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(descriptions(secondPage)).containsExactly("day-2", "day-1");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_withNegativePage_returnsFirstPage() throws Exception {
        saveStandard(4, "only");

        Page<TransactionListItemDTO> page = pageFrom(mockMvc.perform(get(URL).param("page", "-3"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.getNumber()).isZero();
        assertThat(descriptions(page)).containsExactly("only");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_rendersDetailColumns() throws Exception {
        Transaction debit = new Transaction(new BigDecimal("42.50"), currentMonth.atDay(2), Direction.DEBIT,
            TransactionType.STANDARD, "Weekly shop", account, budgetCategory, currentBudget);
        debit.setDescription("Corner Market");
        transactionRepository.save(debit);
        save("1200.00", Direction.CREDIT, TransactionType.STANDARD, currentBudget, currentMonth.atDay(1), "Payroll");

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Living Expenses")))
                .andExpect(content().string(containsString("Groceries")))
                .andExpect(content().string(containsString("Everyday Checking")))
                .andExpect(content().string(containsString(">Checking<")))
                .andExpect(content().string(containsString(">Standard<")))
                .andExpect(content().string(containsString("−$42.50")))
                .andExpect(content().string(containsString("+$1,200.00")))
                .andExpect(content().string(containsString("data-description=\"Corner Market\"")))
                .andExpect(content().string(containsString("data-note=\"Weekly shop\"")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_rendersAddAndBulkUploadButtonsWithoutBudgetScope() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"btnAddTransaction\"")))
                .andExpect(content().string(containsString("id=\"btnBulkUpload\"")))
                .andExpect(content().string(containsString("id=\"addTransactionModal\"")))
                .andExpect(content().string(containsString("id=\"bulkUploadModal\"")))
                // No budget input: the modal saves via /transactions/by-date instead.
                .andExpect(content().string(not(containsString("id=\"txBudgetId\""))));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_categoryDropdownListsCategoriesAcrossAllBudgets() throws Exception {
        Group savings = groupRepository.save(new Group("Savings Goals"));
        categoryRepository.save(new Category("Vacation", savings));
        previousBudget.addGroup(savings);
        previousBudget = budgetRepository.save(previousBudget);

        Group living = budgetCategory.getCategory().getGroup();
        currentBudget.addGroup(living);
        currentBudget = budgetRepository.save(currentBudget);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("label=\"Living Expenses\"")))
                .andExpect(content().string(containsString("label=\"Savings Goals\"")))
                .andExpect(content().string(containsString(">Vacation</option>")))
                .andExpect(content().string(containsString(">Groceries</option>")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_withNoTransactions_showsEmptyState() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No transactions for")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_withHxRequest_returnsTableFragment() throws Exception {
        saveStandard(6, "fragment");

        mockMvc.perform(get(URL).param("page", "0").header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/transactions-table :: transactions-table"))
                .andExpect(content().string(not(containsString("<html"))));
    }

    // ── Isolation ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_excludesOtherUsersTransactions() throws Exception {
        saveStandard(7, "mine");
        save("10.00", Direction.DEBIT, TransactionType.STANDARD, otherUserBudget, currentMonth.atDay(7), "theirs");

        MvcResult result = mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(descriptions(pageFrom(result))).containsExactly("mine");
    }

    // ── Unauthenticated ────────────────────────────────────────────────────────

    @Test
    void getTransactions_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
