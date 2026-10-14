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
    // Second group/category and account type, for the filter tests.
    private Account savingsAccount;
    private BudgetCategory rentBudgetCategory;
    private Account otherUserAccount;

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

        savingsAccount = accountRepository.save(
            new Account(AccountType.SAVINGS, "Rainy Day", new BigDecimal("500.00"), user));
        Group housing = groupRepository.save(new Group("Housing"));
        Category rent = categoryRepository.save(new Category("Rent", housing));
        rentBudgetCategory = budgetCategoryRepository.save(new BudgetCategory(currentBudget, rent, BigDecimal.ZERO));

        otherUserAccount = accountRepository.save(
            new Account(AccountType.CHECKING, "Their Checking", new BigDecimal("1000.00"), other));
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

    /**
     * Saves a STANDARD transaction on the current budget. The page filters on transaction date, not
     * budget, so dates outside the current month are fine here even though the app normally keeps
     * a transaction inside its budget's month.
     */
    private Transaction saveTx(String amount, Direction direction, LocalDate date, String description, String note,
                               Account txAccount, BudgetCategory txCategory) {
        Transaction t = new Transaction(new BigDecimal(amount), date, direction, TransactionType.STANDARD, note,
            txAccount, txCategory, currentBudget);
        t.setDescription(description);
        return transactionRepository.save(t);
    }

    private List<String> descriptionsFor(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return descriptions(pageFrom(mockMvc.perform(request).andExpect(status().isOk()).andReturn()));
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
    void getTransactions_rendersEditAndDeleteButtonsWithTransactionData() throws Exception {
        Transaction tx = new Transaction(new BigDecimal("42.50"), currentMonth.atDay(2), Direction.DEBIT,
            TransactionType.STANDARD, null, account, budgetCategory, currentBudget);
        tx = transactionRepository.save(tx);

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"editTransactionModal\"")))
                .andExpect(content().string(not(containsString("id=\"editTxBudgetId\""))))
                .andExpect(content().string(containsString("onclick=\"openEditTransactionModal(this)\"")))
                .andExpect(content().string(containsString("onclick=\"deleteTransaction(this)\"")))
                .andExpect(content().string(containsString("data-id=\"" + tx.getId() + "\"")))
                .andExpect(content().string(containsString("data-amount=\"42.50\"")))
                .andExpect(content().string(containsString("data-account-id=\"" + account.getId() + "\"")))
                .andExpect(content().string(containsString(
                    "data-category-id=\"" + budgetCategory.getCategory().getId() + "\"")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getTransactions_withPagePastTheEnd_returnsLastPage() throws Exception {
        for (int day = 1; day <= 12; day++) {
            saveStandard(day, "day-" + day);
        }

        Page<TransactionListItemDTO> page = pageFrom(mockMvc.perform(get(URL).param("page", "5"))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(page.getNumber()).isEqualTo(1);
        assertThat(descriptions(page)).containsExactly("day-2", "day-1");
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

    // ── Filters: period ────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void filter_pastThreeMonths_includesCurrentAndTwoPreviousMonthsOnly() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "this-month", null, null, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.minusMonths(2).atDay(1), "two-back", null, null, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.minusMonths(3).atEndOfMonth(), "three-back", null, null, null);

        assertThat(descriptionsFor(get(URL).param("period", "PAST_3_MONTHS")))
            .containsExactly("this-month", "two-back");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filter_forever_includesEverything() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "recent", null, null, null);
        saveTx("10.00", Direction.DEBIT, LocalDate.of(2001, 1, 1), "ancient", null, null, null);

        assertThat(descriptionsFor(get(URL).param("period", "FOREVER"))).containsExactly("recent", "ancient");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filter_customRange_isInclusiveAtBothEnds() throws Exception {
        saveTx("10.00", Direction.DEBIT, LocalDate.of(2025, 3, 9),  "before", null, null, null);
        saveTx("10.00", Direction.DEBIT, LocalDate.of(2025, 3, 10), "start", null, null, null);
        saveTx("10.00", Direction.DEBIT, LocalDate.of(2025, 3, 20), "end", null, null, null);
        saveTx("10.00", Direction.DEBIT, LocalDate.of(2025, 3, 21), "after", null, null, null);

        assertThat(descriptionsFor(get(URL).param("period", "CUSTOM").param("from", "2025-03-10").param("to", "2025-03-20")))
            .containsExactly("end", "start");
        // A reversed range is swapped rather than returning nothing.
        assertThat(descriptionsFor(get(URL).param("period", "CUSTOM").param("from", "2025-03-20").param("to", "2025-03-10")))
            .containsExactly("end", "start");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filter_customDatesAreIgnoredForPresetPeriod() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "this-month", null, null, null);
        saveTx("10.00", Direction.DEBIT, LocalDate.of(2025, 3, 10), "old", null, null, null);

        assertThat(descriptionsFor(get(URL).param("period", "THIS_MONTH").param("from", "2025-03-01").param("to", "2025-03-31")))
            .containsExactly("this-month");
    }

    // ── Filters: transaction type ──────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void filter_byTransactionType_defaultsToStandard() throws Exception {
        save("10.00", Direction.DEBIT, TransactionType.STANDARD,   currentBudget, currentMonth.atDay(3), "standard");
        save("10.00", Direction.DEBIT, TransactionType.ADJUSTMENT, currentBudget, currentMonth.atDay(2), "adjustment");
        save("10.00", Direction.DEBIT, TransactionType.TRANSFER,   currentBudget, currentMonth.atDay(1), "transfer");

        assertThat(descriptionsFor(get(URL))).containsExactly("standard");
        assertThat(descriptionsFor(get(URL).param("type", "ADJUSTMENT"))).containsExactly("adjustment");
        assertThat(descriptionsFor(get(URL).param("type", "TRANSFER"))).containsExactly("transfer");
        assertThat(descriptionsFor(get(URL).param("type", "ALL"))).containsExactly("standard", "adjustment", "transfer");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void nonStandardRows_haveDetailsButNoEditOrDeleteButtons() throws Exception {
        save("10.00", Direction.DEBIT, TransactionType.TRANSFER, currentBudget, currentMonth.atDay(1), "transfer");

        mockMvc.perform(get(URL).param("type", "TRANSFER"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("tx-details-btn")))
                .andExpect(content().string(not(containsString("onclick=\"openEditTransactionModal(this)\""))))
                .andExpect(content().string(not(containsString("onclick=\"deleteTransaction(this)\""))));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void fullPage_rendersTypeFilterWithStandardSelectedByDefault() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"STANDARD\"\\s+selected=\"selected\">.*")))
                .andExpect(content().string(containsString("<option value=\"ALL\">All types</option>")));
    }

    // ── Filters: group / category / account ────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void filter_byGroupAndCategory() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(3), "groceries", null, null, budgetCategory);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(2), "rent", null, null, rentBudgetCategory);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "loose", null, null, null);

        Long livingGroupId = budgetCategory.getCategory().getGroup().getId();
        Long rentCategoryId = rentBudgetCategory.getCategory().getId();

        assertThat(descriptionsFor(get(URL).param("groupId", livingGroupId.toString()))).containsExactly("groceries");
        assertThat(descriptionsFor(get(URL).param("categoryId", rentCategoryId.toString()))).containsExactly("rent");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filter_uncategorized_returnsOnlyUncategorizedAndOverridesCategory() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(3), "groceries", null, null, budgetCategory);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "loose", null, null, null);

        assertThat(descriptionsFor(get(URL).param("uncategorized", "true"))).containsExactly("loose");
        assertThat(descriptionsFor(get(URL)
                .param("uncategorized", "true")
                .param("categoryId", budgetCategory.getCategory().getId().toString())))
            .containsExactly("loose");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filter_byAccountTypeAndAccount() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(3), "checking", null, account, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(2), "savings", null, savingsAccount, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "no-account", null, null, null);

        assertThat(descriptionsFor(get(URL).param("accountType", "SAVINGS"))).containsExactly("savings");
        assertThat(descriptionsFor(get(URL).param("accountId", account.getId().toString()))).containsExactly("checking");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filter_neverReturnsOtherUsersTransactions() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "mine", null, null, null);
        Transaction theirs = new Transaction(new BigDecimal("10.00"), currentMonth.atDay(1), Direction.DEBIT,
            TransactionType.STANDARD, null, otherUserAccount, null, otherUserBudget);
        theirs.setDescription("theirs");
        transactionRepository.save(theirs);

        assertThat(descriptionsFor(get(URL).param("period", "FOREVER"))).containsExactly("mine");
        assertThat(descriptionsFor(get(URL).param("accountId", otherUserAccount.getId().toString()))).isEmpty();
        assertThat(descriptionsFor(get(URL).param("q", "theirs"))).isEmpty();
    }

    // ── Search ─────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void search_matchesDescriptionOrNoteCaseInsensitively() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(3), "Morning COFFEE", null, null, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(2), "Bakery", "coffee and croissant", null, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "Fuel", "highway", null, null);

        assertThat(descriptionsFor(get(URL).param("q", "  Coffee "))).containsExactly("Morning COFFEE", "Bakery");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void search_treatsLikeWildcardsLiterally() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(2), "100% refund", null, null, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "1000 refund", null, null, null);

        assertThat(descriptionsFor(get(URL).param("q", "100%"))).containsExactly("100% refund");
        assertThat(descriptionsFor(get(URL).param("q", "_"))).isEmpty();
    }

    // ── Sort ───────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void sort_byDateAscending() throws Exception {
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(5), "day-5", null, null, null);
        saveTx("10.00", Direction.DEBIT, currentMonth.atDay(1), "day-1", null, null, null);

        assertThat(descriptionsFor(get(URL).param("sort", "DATE_ASC"))).containsExactly("day-1", "day-5");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void sort_byAmountUsesSizeRegardlessOfDirection() throws Exception {
        saveTx("12.00",  Direction.DEBIT,  currentMonth.atDay(1), "small-debit", null, null, null);
        saveTx("500.00", Direction.CREDIT, currentMonth.atDay(2), "mid-credit", null, null, null);
        saveTx("900.00", Direction.DEBIT,  currentMonth.atDay(3), "big-debit", null, null, null);

        assertThat(descriptionsFor(get(URL).param("sort", "AMOUNT_DESC")))
            .containsExactly("big-debit", "mid-credit", "small-debit");
        assertThat(descriptionsFor(get(URL).param("sort", "AMOUNT_ASC")))
            .containsExactly("small-debit", "mid-credit", "big-debit");
    }

    // ── Rendering ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void pagination_linksKeepActiveFilters() throws Exception {
        for (int day = 1; day <= 12; day++) {
            saveTx("10.00", Direction.DEBIT, currentMonth.atDay(day), "coffee " + day, null, null, null);
        }

        mockMvc.perform(get(URL).param("q", "coffee").param("sort", "AMOUNT_ASC").param("groupId", ""))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("href=\"/transactions?q=coffee&amp;sort=AMOUNT_ASC&amp;page=1\"")))
                .andExpect(content().string(not(containsString("groupId="))));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void fullPage_rendersFilterFormWithSelectedValues() throws Exception {
        mockMvc.perform(get(URL)
                        .param("period", "CUSTOM").param("from", "2025-03-01").param("to", "2025-03-31")
                        .param("accountType", "SAVINGS").param("q", "rent"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("id=\"txFilterForm\"")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"CUSTOM\"\\s+selected=\"selected\">.*")))
                .andExpect(content().string(matchesPattern("(?s).*<option value=\"SAVINGS\"\\s+selected=\"selected\">.*")))
                .andExpect(content().string(containsString("value=\"2025-03-01\"")))
                .andExpect(content().string(containsString("value=\"rent\"")))
                .andExpect(content().string(containsString("data-account-type=\"SAVINGS\"")))
                .andExpect(content().string(containsString("Mar 1 – Mar 31, 2025")));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void filteredEmptyResult_showsResetLink() throws Exception {
        mockMvc.perform(get(URL).param("q", "nothing-matches"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No transactions match these filters")))
                .andExpect(content().string(containsString("Reset filters")));
    }
}
