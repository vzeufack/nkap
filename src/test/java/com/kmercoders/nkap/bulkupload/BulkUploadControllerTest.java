package com.kmercoders.nkap.bulkupload;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.account.Account;
import com.kmercoders.nkap.account.AccountRepository;
import com.kmercoders.nkap.account.AccountType;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.financialinstitution.FinancialInstitution;
import com.kmercoders.nkap.financialinstitution.FinancialInstitutionRepository;
import com.kmercoders.nkap.transaction.TransactionRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Month;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BulkUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private AccountRepository accountRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private TransactionRepository transactionRepository;
    @Autowired private FinancialInstitutionRepository financialInstitutionRepository;
    @Autowired private PasswordEncoder passwordEncoder;

    private static final String EMAIL = "bulkupload_user@example.com";
    private static final String PREVIEW_URL = "/bulk-upload/preview";
    private static final String CONFIRM_URL = "/bulk-upload/confirm";

    private Long bofaCheckingAccountId;
    private Long bofaCreditAccountId;
    private Long capitalOneCheckingAccountId;
    private Long capitalOneCreditAccountId;
    private Long noInstitutionAccountId;
    private Long cashAccountId;

    private static final String VALID_CHECKING_SAMPLE = """
        Description,,Summary Amt.
        Beginning balance as of 07/09/2026,,"3,138.59"
        Total credits,,"2,997.13"
        Total debits,,"-4,743.58"
        Ending balance as of 07/29/2026,,"1,392.14"

        Date,Description,Amount,Running Bal.
        07/09/2026,Beginning balance as of 07/09/2026,,"3,138.59"
        07/09/2026,"TapTap Send US 07/08 PMNT SENT 8339160670 DE","-18.35","3,120.24"
        07/13/2026,"TapTap Send US 07/12 PMNT SENT 8339160670 DE","-87.12","3,033.12"
        07/14/2026,"Zelle Recurring payment to Hawa for ""T-Mobile bill""; Conf# yidxfcru5","-68.80","2,964.32"
        07/14/2026,"COMCAST-XFINITY DES:CABLE SVCS ID:7635267 INDN:VANNEL *ZEUFACK CO ID:0000213249 PPD","-105.20","2,859.12"
        07/17/2026,"NEXTEP PAYROLL DES:PAYROLL ID:000680 INDN:VANNEL ZEUFACK CO ID:9946759001 PPD","2,997.13","5,856.25"
        07/17/2026,"TapTap Send US 07/16 PMNT SENT 8339160670 DE","-9.64","5,846.61"
        07/20/2026,"Online Banking payment to CRD 9991 Confirmation# tv5m2zefo","-554.57","5,292.04"
        07/20/2026,"Online Banking transfer to SAV 8732 Confirmation# 7248240149","-3,200.00","2,092.04"
        07/20/2026,"CAPITAL ONE DES:ONLINE PMT ID:CA02D317D0FAFD1 INDN:Vannel Zeufack CO ID:9279744391 WEB","-19.99","2,072.05"
        07/22/2026,"Zelle payment to Soin Traore for ""Movie""; Conf# yf0li6d2q","-8.42","2,063.63"
        07/22/2026,"Zelle payment to Maman Martine for ""Pour Maman Penko Helene""; Conf# uepfrmv6y","-250.00","1,813.63"
        07/22/2026,"GPC DES:GPC EFT ID:1704848092MEE INDN:Vannel Zeufack CO ID:1580257110 PPD","-131.49","1,682.14"
        07/24/2026,"Zelle payment to Treadmill for ""Nordick Treadmill""; Conf# uccyrc9pw","-290.00","1,392.14"
        """;

    private static final String VALID_CREDIT_SAMPLE = """
        Posted Date,Reference Number,Payee,Address,Amount
        07/28/2026,02305376208300344823386,"NAM DAE MUN FARMERS MA STONE MOUNTAIGA","STONE MOUNTAI GA ",-34.07
        07/27/2026,75454916207900012100014,"HONG KONG GARDEN LITHONIA GA","LITHONIA      GA ",-13.50
        07/27/2026,55432866207209205540986,"QT 747 LITHONIA GA","LITHONIA      GA ",-25.00
        07/27/2026,55421356207630195609991,"PMUSA 714086 GEORGIA T ATLANTA GA","ATLANTA       GA ",-5.45
        07/27/2026,55483826207027570273849,"WAL-MART #4472 LITHONIA GA","LITHONIA      GA ",-19.38
        07/27/2026,55432866207209055527059,"LIDL #1290 DECATUR GA","DECATUR       GA ",-20.06
        07/27/2026,55432866206208842129493,"QT 1721 ALPHARETTA GA","ALPHARETTA    GA ",-19.75
        07/27/2026,55432866206208842129477,"QT 1721 ALPHARETTA GA","ALPHARETTA    GA ",-5.59
        07/25/2026,55432866205208506582616,"APPLE.COM/BILL CUPERTINO CA","CUPERTINO     CA ",-7.99
        07/24/2026,02703406205016910718521,"Spotify P44E986362 New York NY","New York      NY ",-18.99
        07/22/2026,02305376203500372057345,"TST* STUDIO MOVIE GRIL DULUTH GA","DULUTH        GA ",-21.02
        07/22/2026,55316586203830755948797,"BP#5818521COVINGTONQPS LITHONIA GA","LITHONIA      GA ",-38.26
        07/20/2026,55483826200027244766662,"WAL-MART #1340 LITHONIA GA","LITHONIA      GA ",-102.48
        07/20/2026,19920401050086416139264,"PAYMENT FROM CHK 7216 CONF#tv5m2zefo","",554.57
        07/18/2026,55432866199206560898520,"QDOBA 2605 ALPHARETTA GA","ALPHARETTA    GA ",-11.84
        """;

    private static final String VALID_CAPITAL_ONE_CREDIT_SAMPLE = """
        Transaction Date,Posted Date,Card No.,Description,Category,Debit,Credit
        2026-07-08,2026-07-09,2043,AMAZON MKTPLACE PMTS,Merchandise,,32.39
        2026-07-07,2026-07-07,2043,AMAZON MKTPL*CM4T050F3,Merchandise,32.39,
        2026-07-01,2026-07-01,2043,CAPITAL ONE MOBILE PYMT,Payment/Credit,,458.08
        2026-06-27,2026-06-27,2043,LAWNCARE* LAWNSTARTER,Other Services,63.39,
        2026-06-22,2026-06-23,2043,AMAZON MKTPL*P55G17403,Merchandise,32.39,
        2026-06-19,2026-06-22,2043,GW LOVEJOY COURT,Other Services,150.00,
        2026-06-19,2026-06-20,2043,GW SERV-FEE,Other Services,9.00,
        2026-06-18,2026-06-19,2043,VIVINT INC/US,Other Services,51.64,
        2026-06-18,2026-06-18,2043,CAPITAL ONE MOBILE PYMT,Payment/Credit,,241.40
        2026-06-17,2026-06-18,2043,ANTHROPIC* CLAUDE SUB,Merchandise,200.00,
        2026-06-16,2026-06-18,2043,AMAZON RETA* BB9WE0K43,Merchandise,,48.34
        2026-06-16,2026-06-17,2043,AMAZON MKTPLACE PMTS,Merchandise,,16.12
        2026-06-15,2026-06-15,2043,FuboTV Inc,Phone/Cable,55.99,
        2026-06-10,2026-06-12,2043,DISNEY EC PARKING,Entertainment,35.00,
        """;

    @BeforeAll
    void setUp() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        accountRepository.deleteAll();
        appUserRepository.deleteAll();

        AppUser user = new AppUser(EMAIL, passwordEncoder.encode("somepassword"));
        appUserRepository.save(user);

        FinancialInstitution bankOfAmerica = financialInstitutionRepository.findByKey("BANK_OF_AMERICA").orElseThrow();
        FinancialInstitution capitalOne = financialInstitutionRepository.findByKey("CAPITAL_ONE").orElseThrow();

        Account bofaChecking = new Account(AccountType.CHECKING, "BofA Checking", BigDecimal.ZERO, user);
        bofaChecking.setFinancialInstitution(bankOfAmerica);
        accountRepository.save(bofaChecking);
        bofaCheckingAccountId = bofaChecking.getId();

        Account bofaCredit = new Account(AccountType.CREDIT, "BofA Credit", BigDecimal.ZERO, user);
        bofaCredit.setFinancialInstitution(bankOfAmerica);
        accountRepository.save(bofaCredit);
        bofaCreditAccountId = bofaCredit.getId();

        Account capitalOneChecking = new Account(AccountType.CHECKING, "Capital One Checking", BigDecimal.ZERO, user);
        capitalOneChecking.setFinancialInstitution(capitalOne);
        accountRepository.save(capitalOneChecking);
        capitalOneCheckingAccountId = capitalOneChecking.getId();

        Account capitalOneCredit = new Account(AccountType.CREDIT, "Capital One Credit", BigDecimal.ZERO, user);
        capitalOneCredit.setFinancialInstitution(capitalOne);
        accountRepository.save(capitalOneCredit);
        capitalOneCreditAccountId = capitalOneCredit.getId();

        Account noInstitutionAccount = new Account(AccountType.CHECKING, "No Institution", BigDecimal.ZERO, user);
        accountRepository.save(noInstitutionAccount);
        noInstitutionAccountId = noInstitutionAccount.getId();

        Account cashAccount = new Account(AccountType.CASH, "Cash", BigDecimal.ZERO, user);
        cashAccount.setFinancialInstitution(bankOfAmerica);
        accountRepository.save(cashAccount);
        cashAccountId = cashAccount.getId();
    }

    @BeforeEach
    void clearTransactionsAndBudgets() {
        transactionRepository.deleteAll();
        budgetRepository.deleteAll();
        accountRepository.findById(bofaCheckingAccountId).ifPresent(a -> {
            a.setBalance(BigDecimal.ZERO);
            accountRepository.save(a);
        });
        accountRepository.findById(bofaCreditAccountId).ifPresent(a -> {
            a.setBalance(BigDecimal.ZERO);
            accountRepository.save(a);
        });
        accountRepository.findById(capitalOneCreditAccountId).ifPresent(a -> {
            a.setBalance(BigDecimal.ZERO);
            accountRepository.save(a);
        });
    }

    private MockMultipartFile csvFile(String content) {
        return new MockMultipartFile("file", "statement.csv", "text/csv", content.getBytes(StandardCharsets.UTF_8));
    }

    // ── Preview: happy path ──

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withValidBofaCheckingCsv_returns200AndPreview() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", bofaCheckingAccountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountId", is(bofaCheckingAccountId.intValue())))
                .andExpect(jsonPath("$.totalRows", is(13)))
                .andExpect(jsonPath("$.rows", hasSize(13)))
                .andExpect(jsonPath("$.budgetsToCreate", hasSize(1)))
                .andExpect(jsonPath("$.budgetsToCreate[0].month", is("JULY")))
                .andExpect(jsonPath("$.budgetsToCreate[0].year", is(2026)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withValidBofaCreditCsv_returns200AndPreview() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CREDIT_SAMPLE))
                        .param("accountId", bofaCreditAccountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(15)))
                .andExpect(jsonPath("$.rows", hasSize(15)));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withValidCapitalOneCreditCsv_returns200AndPreview() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CAPITAL_ONE_CREDIT_SAMPLE))
                        .param("accountId", capitalOneCreditAccountId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows", is(14)))
                .andExpect(jsonPath("$.rows", hasSize(14)))
                .andExpect(jsonPath("$.budgetsToCreate", hasSize(2)));
    }

    // ── Preview: validation failures ──

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withWrongFormatCsv_returns400WithErrors() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CREDIT_SAMPLE))
                        .param("accountId", bofaCheckingAccountId.toString()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors", not(empty())));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withCapitalOneCheckingAccount_returns400() throws Exception {
        // Capital One Checking/Savings isn't supported yet — only Capital One Credit is.
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", capitalOneCheckingAccountId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withAccountMissingFinancialInstitution_returns400() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", noInstitutionAccountId.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withCashAccountType_returns400() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", cashAccountId.toString()))
                .andExpect(status().isBadRequest());
    }

    // ── Preview: cross-user isolation & security ──

    @Test
    @WithMockUser(username = EMAIL)
    void preview_withAnotherUsersAccount_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_bulkupload@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Account otherAccount = new Account(AccountType.CHECKING, "Other Account", BigDecimal.ZERO, otherUser);
        otherAccount.setFinancialInstitution(financialInstitutionRepository.findByKey("BANK_OF_AMERICA").orElseThrow());
        accountRepository.save(otherAccount);

        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", otherAccount.getId().toString()))
                .andExpect(status().isNotFound());
    }

    @Test
    void preview_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", bofaCheckingAccountId.toString()))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }

    // ── Confirm: happy path ──

    @Test
    @WithMockUser(username = EMAIL)
    void confirmUpload_withValidPreviewedRows_persistsTransactionsAndCreatesBudget() throws Exception {
        String previewResponse = mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CHECKING_SAMPLE))
                        .param("accountId", bofaCheckingAccountId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode previewJson = objectMapper.readTree(previewResponse);
        List<Map<String, Object>> transactions = new ArrayList<>();
        for (JsonNode row : previewJson.get("rows")) {
            Map<String, Object> tx = new HashMap<>();
            tx.put("transactionDate", row.get("transactionDate").asText());
            tx.put("amount", row.get("amount").asDouble());
            tx.put("direction", row.get("direction").asText());
            tx.put("description", row.get("description").asText());
            transactions.add(tx);
        }

        Map<String, Object> confirmPayload = new HashMap<>();
        confirmPayload.put("accountId", bofaCheckingAccountId);
        confirmPayload.put("transactions", transactions);

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated", is(13)))
                .andExpect(jsonPath("$.budgetsCreated", is(1)));

        assertThat(transactionRepository.count()).isEqualTo(13);
        assertThat(budgetRepository.existsByAppUserAndMonthAndYear(
                appUserRepository.findByEmail(EMAIL).orElseThrow(), Month.JULY, 2026))
            .isTrue();
        assertThat(accountRepository.findById(bofaCheckingAccountId).orElseThrow().getBalance())
            .isEqualByComparingTo(new BigDecimal("-1746.45"));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void confirmUpload_withValidCapitalOneCreditPreviewedRows_persistsTransactionsAndCreatesBudgets() throws Exception {
        String previewResponse = mockMvc.perform(multipart(PREVIEW_URL)
                        .file(csvFile(VALID_CAPITAL_ONE_CREDIT_SAMPLE))
                        .param("accountId", capitalOneCreditAccountId.toString()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        JsonNode previewJson = objectMapper.readTree(previewResponse);
        List<Map<String, Object>> transactions = new ArrayList<>();
        for (JsonNode row : previewJson.get("rows")) {
            Map<String, Object> tx = new HashMap<>();
            tx.put("transactionDate", row.get("transactionDate").asText());
            tx.put("amount", row.get("amount").asDouble());
            tx.put("direction", row.get("direction").asText());
            tx.put("description", row.get("description").asText());
            transactions.add(tx);
        }

        Map<String, Object> confirmPayload = new HashMap<>();
        confirmPayload.put("accountId", capitalOneCreditAccountId);
        confirmPayload.put("transactions", transactions);

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(confirmPayload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionsCreated", is(14)))
                .andExpect(jsonPath("$.budgetsCreated", is(2)));

        assertThat(accountRepository.findById(capitalOneCreditAccountId).orElseThrow().getBalance())
            .isEqualByComparingTo(new BigDecimal("166.53"));
    }

    // ── Confirm: validation / security ──

    @Test
    @WithMockUser(username = EMAIL)
    void confirmUpload_withEmptyTransactionsList_returns400WithFieldError() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", bofaCheckingAccountId);
        payload.put("transactions", List.of());

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.transactions", notNullValue()));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void confirmUpload_withAnotherUsersAccount_returns404() throws Exception {
        AppUser otherUser = new AppUser("other_confirm@example.com", passwordEncoder.encode("pass"));
        appUserRepository.save(otherUser);
        Account otherAccount = new Account(AccountType.CHECKING, "Other Confirm Account", BigDecimal.ZERO, otherUser);
        otherAccount.setFinancialInstitution(financialInstitutionRepository.findByKey("BANK_OF_AMERICA").orElseThrow());
        accountRepository.save(otherAccount);

        Map<String, Object> tx = new HashMap<>();
        tx.put("transactionDate", "2026-07-09");
        tx.put("amount", 18.35);
        tx.put("direction", "DEBIT");
        tx.put("description", "Test");

        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", otherAccount.getId());
        payload.put("transactions", List.of(tx));

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().isNotFound());
    }

    @Test
    void confirmUpload_withoutAuthentication_returns401or302() throws Exception {
        Map<String, Object> payload = new HashMap<>();
        payload.put("accountId", bofaCheckingAccountId);
        payload.put("transactions", List.of());

        mockMvc.perform(post(CONFIRM_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(payload)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
