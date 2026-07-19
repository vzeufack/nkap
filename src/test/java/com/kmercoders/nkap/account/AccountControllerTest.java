package com.kmercoders.nkap.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;
import com.kmercoders.nkap.transaction.TransactionRepository;
import com.kmercoders.nkap.transaction.TransactionRequest;
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
import java.time.LocalDate;
import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetService budgetService;
    @Autowired private TransactionRepository transactionRepository;

    private static final String EMAIL = "account_user@example.com";
    private static final String URL   = "/accounts";

    @BeforeAll
    void createUser() {
        appUserRepository.deleteAll();
        AppUser user = new AppUser(EMAIL, passwordEncoder.encode("somepassword"));
        appUserRepository.save(user);
    }

    @BeforeEach
    void setUp() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        accountRepository.deleteAll();
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private AccountRequest request(String name, AccountType type, BigDecimal balance) {
        AccountRequest r = new AccountRequest();
        r.setName(name);
        r.setAccountType(type);
        r.setBalance(balance);
        return r;
    }

    // ── Create: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withValidFields_returns200AndCorrectPayload() throws Exception {
        AccountRequest req = request("Main Checking", AccountType.CHECKING, new BigDecimal("1500.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id",          notNullValue()))
                .andExpect(jsonPath("$.name",         is("Main Checking")))
                .andExpect(jsonPath("$.accountType",  is("CHECKING")))
                .andExpect(jsonPath("$.balance",      is(1500.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_persistsToDatabase() throws Exception {
        AccountRequest req = request("Savings Account", AccountType.SAVINGS, new BigDecimal("500.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk());

        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        List<Account> accounts = accountRepository.findByAppUser(user);
        assertThat(accounts).hasSize(1);
        assertThat(accounts.get(0).getName()).isEqualTo("Savings Account");
        assertThat(accounts.get(0).getType()).isEqualTo(AccountType.SAVINGS);
        assertThat(accounts.get(0).getBalance()).isEqualByComparingTo("500.00");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withNegativeBalance_returns200() throws Exception {
        AccountRequest req = request("Credit Card", AccountType.CREDIT, new BigDecimal("-250.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(-250.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withZeroBalance_returns200() throws Exception {
        AccountRequest req = request("Cash Wallet", AccountType.CASH, BigDecimal.ZERO);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance", is(0)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_accountTypesAreAllAccepted() throws Exception {
        for (AccountType type : AccountType.values()) {
            AccountRequest req = request(type.name() + " account", type, BigDecimal.ZERO);

            mockMvc.perform(post(URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.accountType", is(type.name())));
        }
    }

    // ── Create: Validation failures ────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withBlankName_returns400WithFieldError() throws Exception {
        AccountRequest req = request("", AccountType.CHECKING, new BigDecimal("100.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withNameExceedingMaxLength_returns400WithFieldError() throws Exception {
        AccountRequest req = request("A".repeat(101), AccountType.CHECKING, new BigDecimal("100.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withNullAccountType_returns400WithFieldError() throws Exception {
        AccountRequest req = request("My Account", null, new BigDecimal("100.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accountType", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createAccount_withNullBalance_returns400WithFieldError() throws Exception {
        AccountRequest req = request("My Account", AccountType.CHECKING, null);

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.balance", notNullValue()));
    }

    // ── List: Happy path ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void getAccounts_withNoAccounts_returnsEmptyList() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getAccounts_returnsAllCreatedAccounts() throws Exception {
        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Checking", AccountType.CHECKING, new BigDecimal("1000.00")))))
                .andExpect(status().isOk());

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Savings", AccountType.SAVINGS, new BigDecimal("2000.00")))))
                .andExpect(status().isOk());

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void getAccounts_doesNotReturnOtherUsersAccounts() throws Exception {
        AppUser otherUser = new AppUser("other@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        accountRepository.save(new Account(AccountType.CHECKING, "Other Account", BigDecimal.ZERO, otherUser));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    void createAccount_withoutAuthentication_returns401or302() throws Exception {
        AccountRequest req = request("My Account", AccountType.CHECKING, new BigDecimal("100.00"));

        mockMvc.perform(post(URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    @Test
    void getAccounts_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Update: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_withValidFields_returns200AndUpdatedPayload() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Old Name", new BigDecimal("100.00"), user));

        AccountRequest req = request("New Name", AccountType.SAVINGS, new BigDecimal("999.00"));

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andDo(print())
                .andExpect(jsonPath("$.id",         is(saved.getId().intValue())))
                .andExpect(jsonPath("$.name",        is("New Name")))
                .andExpect(jsonPath("$.accountType", is("SAVINGS")))
                .andExpect(jsonPath("$.balance",     is(999.00)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_persistsChangesToDatabase() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Old Name", new BigDecimal("100.00"), user));

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Updated Name", AccountType.CASH, new BigDecimal("250.00")))))
                .andExpect(status().isOk());

        Account updated = accountRepository.findById(saved.getId()).orElseThrow();
        assertThat(updated.getName()).isEqualTo("Updated Name");
        assertThat(updated.getType()).isEqualTo(AccountType.CASH);
        assertThat(updated.getBalance()).isEqualByComparingTo("250.00");
    }

    // ── Update: Access isolation ───────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(put(URL + "/999999")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Name", AccountType.CHECKING, BigDecimal.ZERO))))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_cannotUpdateAnotherUsersAccount_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_edit@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Account otherAccount = accountRepository.save(
                new Account(AccountType.CHECKING, "Other Account", BigDecimal.ZERO, otherUser));

        mockMvc.perform(put(URL + "/" + otherAccount.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Hijacked", AccountType.SAVINGS, new BigDecimal("9999.00")))))
                .andExpect(status().isNotFound());
    }

    // ── Update: Validation failures ────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_withBlankName_returns400WithFieldError() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Valid", BigDecimal.ZERO, user));

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("", AccountType.CHECKING, BigDecimal.ZERO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_withNameExceedingMaxLength_returns400WithFieldError() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Valid", BigDecimal.ZERO, user));

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("A".repeat(101), AccountType.CHECKING, BigDecimal.ZERO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.name", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_withNullAccountType_returns400WithFieldError() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Valid", BigDecimal.ZERO, user));

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Name", null, BigDecimal.ZERO))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.accountType", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateAccount_withNullBalance_returns400WithFieldError() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Valid", BigDecimal.ZERO, user));

        mockMvc.perform(put(URL + "/" + saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Name", AccountType.CHECKING, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.balance", notNullValue()));
    }

    // ── Update: Security ───────────────────────────────────────────────────────

    @Test
    void updateAccount_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(put(URL + "/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                request("Name", AccountType.CHECKING, BigDecimal.ZERO))))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Delete: Happy path ─────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void deleteAccount_withZeroBalanceAndNoTransactions_returns204() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Empty", BigDecimal.ZERO, user));

        mockMvc.perform(delete(URL + "/" + saved.getId()))
                .andExpect(status().isNoContent());

        assertThat(accountRepository.findById(saved.getId())).isEmpty();
    }

    // ── Delete: Domain failures ────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void deleteAccount_withNonZeroBalance_returns400AndDoesNotDelete() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "Funded", new BigDecimal("25.00"), user));

        mockMvc.perform(delete(URL + "/" + saved.getId()))
                .andExpect(status().isBadRequest());

        assertThat(accountRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteAccount_withLinkedTransaction_returns400AndDoesNotDelete() throws Exception {
        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Account saved = accountRepository.save(new Account(AccountType.CHECKING, "InUse", BigDecimal.ZERO, user));
        Budget budget = budgetService.createBudget(user, Month.JANUARY, 2025);

        TransactionRequest txReq = new TransactionRequest();
        txReq.setAmount(new BigDecimal("10.00"));
        txReq.setTransactionDate(LocalDate.of(2025, 1, 15));
        txReq.setTransactionType(TransactionType.DEBIT);
        txReq.setAccountId(saved.getId());
        txReq.setBudgetId(budget.getId());

        mockMvc.perform(post("/transactions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(txReq)))
                .andExpect(status().isOk());

        mockMvc.perform(delete(URL + "/" + saved.getId()))
                .andExpect(status().isBadRequest());

        assertThat(accountRepository.findById(saved.getId())).isPresent();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void deleteAccount_withNonExistentId_returns404() throws Exception {
        mockMvc.perform(delete(URL + "/999999"))
                .andExpect(status().isNotFound());
    }

    // ── Delete: Security ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void deleteAccount_cannotDeleteAnotherUsersAccount_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_delete@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Account otherAccount = accountRepository.save(
                new Account(AccountType.CHECKING, "Other Account", BigDecimal.ZERO, otherUser));

        mockMvc.perform(delete(URL + "/" + otherAccount.getId()))
                .andExpect(status().isNotFound());

        assertThat(accountRepository.findById(otherAccount.getId())).isPresent();
    }

    @Test
    void deleteAccount_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(delete(URL + "/1"))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}