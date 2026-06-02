package com.kmercoders.nkap.category;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;
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
class CategoryControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetService budgetService;
    @Autowired private GroupRepository groupRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetCategoryRepository budgetCategoryRepository;
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
    void createCategory_withNullAllocation_returns400WithFieldError() throws Exception {
        CategoryRequest req = request("Meat", null, null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.allocation", notNullValue()));
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
    void createCategory_withNegativeBalance_returns400WithFieldError() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), new BigDecimal("-5.00"));

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.balance", notNullValue()));
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

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    void createCategory_withoutAuthentication_returns401or302() throws Exception {
        CategoryRequest req = request("Meat", new BigDecimal("100.00"), null);

        mockMvc.perform(post(url())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}