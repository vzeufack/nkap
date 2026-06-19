package com.kmercoders.nkap.budget;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.LocalDate;
import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import com.kmercoders.nkap.category.BudgetCategory;
import com.kmercoders.nkap.category.BudgetCategoryRepository;
import com.kmercoders.nkap.category.Category;
import com.kmercoders.nkap.category.CategoryRepository;
import com.kmercoders.nkap.group.Group;
import com.kmercoders.nkap.group.GroupRepository;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private GroupRepository groupRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private BudgetCategoryRepository budgetCategoryRepository;

    private static final String EMAIL    = "budget_user@example.com";
    private static final String PASSWORD = "somepassword";
    private static final Month  MONTH    = Month.JUNE;
    private static final int    YEAR     = 2025;

    @BeforeAll  // runs once before @WithUserDetails attempts the lookup
    void createUser() {
        appUserRepository.deleteAll();
        AppUser user = new AppUser(EMAIL, passwordEncoder.encode(PASSWORD));
        appUserRepository.save(user);
    }

    @BeforeEach  // only clears budgets between tests — user stays intact
    void setUp() {
        budgetRepository.deleteAll();
        budgetCategoryRepository.deleteAll();
        categoryRepository.deleteAll();
        budgetRepository.deleteAll();
        groupRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createBudget_shouldSaveBudgetAndRedirect_whenValidRequest() throws Exception {
        mockMvc.perform(post("/budgets/create/{month}/{year}", MONTH, YEAR)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budgets/JUNE/2025"))
                .andExpect(flash().attribute("success", "Budget created successfully."));

        assertThat(budgetRepository.existsByAppUserAndMonthAndYear(
                appUserRepository.findByEmail(EMAIL).orElseThrow(),
                MONTH,
                YEAR
        )).isTrue();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createBudget_shouldRejectDuplicate_whenBudgetAlreadyExists() throws Exception {
        // First request — should succeed and seed the DB
        mockMvc.perform(post("/budgets/create/{month}/{year}", MONTH, YEAR)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("success", "Budget created successfully."));

        // Second request for the same month/year — should be rejected
        mockMvc.perform(post("/budgets/create/{month}/{year}", MONTH, YEAR)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/budgets/" + MONTH + "/" + YEAR))
                .andExpect(flash().attribute("error", "A budget for JUNE 2025 already exists."));

        // Only one budget should exist in the database, never two
        assertThat(budgetRepository.countByAppUserAndMonthAndYear(
                appUserRepository.findByEmail(EMAIL).orElseThrow(),
                MONTH,
                YEAR
        )).isEqualTo(1);
    }

    // ── showCurrentBudget (/budgets/) ────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void showCurrentBudget_shouldReturnHomeView_whenBudgetExists() throws Exception {
        // Seed a budget for the current month/year
        AppUser appUser = appUserRepository.findByEmail(EMAIL).orElseThrow();
        LocalDate today = LocalDate.now();
        Month currentMonth = today.getMonth();
        int currentYear = today.getYear();

        Budget budget = new Budget();
        budget.setAppUser(appUser);
        budget.setMonth(currentMonth);
        budget.setYear(currentYear);
        budgetRepository.save(budget);

        mockMvc.perform(get("/budgets/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attributeExists("appUser"))
                .andExpect(model().attribute("budget",
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(budget.getId()))))
                .andExpect(model().attribute("currentMonth", currentMonth + " " + currentYear))
                .andExpect(model().attribute("currentMonthValue", currentMonth.name()))
                .andExpect(model().attribute("currentYearValue", currentYear));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void showCurrentBudget_shouldReturnHomeView_whenNoBudgetExists() throws Exception {
        // No budget seeded — controller should pass null to the model
        LocalDate today = LocalDate.now();
        Month currentMonth = today.getMonth();
        int currentYear = today.getYear();

        mockMvc.perform(get("/budgets/"))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attributeExists("appUser"))
                .andExpect(model().attribute("budget", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("currentMonth", currentMonth + " " + currentYear))
                .andExpect(model().attribute("currentMonthValue", currentMonth.name()))
                .andExpect(model().attribute("currentYearValue", currentYear));
    }

    // ── showBudget (/{month}/{year}) ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = EMAIL)
    void showBudget_shouldReturnHomeView_whenNoHtmxHeader() throws Exception {
        // Seed a budget for the test month/year
        AppUser appUser = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Budget budget = new Budget();
        budget.setAppUser(appUser);
        budget.setMonth(MONTH);
        budget.setYear(YEAR);
        budgetRepository.save(budget);

        mockMvc.perform(get("/budgets/{month}/{year}", MONTH, YEAR))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("budget",
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(budget.getId()))))
                .andExpect(model().attribute("currentMonth", MONTH + " " + YEAR))
                .andExpect(model().attribute("currentMonthValue", MONTH.name()))
                .andExpect(model().attribute("currentYearValue", YEAR));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void showBudget_shouldReturnFragment_whenHtmxHeaderPresent() throws Exception {
        AppUser appUser = appUserRepository.findByEmail(EMAIL).orElseThrow();
        Budget budget = new Budget();
        budget.setAppUser(appUser);
        budget.setMonth(MONTH);
        budget.setYear(YEAR);
        budgetRepository.save(budget);

        mockMvc.perform(get("/budgets/{month}/{year}", MONTH, YEAR)
                    .header("HX-Request", "true"))
                .andExpect(status().isOk())
                .andExpect(view().name("fragments/budget-plan :: budget-plan"))
                .andExpect(model().attribute("budget",
                    org.hamcrest.Matchers.hasProperty("id", org.hamcrest.Matchers.is(budget.getId()))))
                .andExpect(model().attribute("currentMonth", MONTH + " " + YEAR))
                .andExpect(model().attribute("currentMonthValue", MONTH.name()))
                .andExpect(model().attribute("currentYearValue", YEAR));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void showBudget_shouldPassNullBudget_whenNoBudgetExistsForPeriod() throws Exception {
        // No budget seeded for MONTH/YEAR
        mockMvc.perform(get("/budgets/{month}/{year}", MONTH, YEAR))
                .andExpect(status().isOk())
                .andExpect(view().name("home"))
                .andExpect(model().attribute("budget", org.hamcrest.Matchers.nullValue()))
                .andExpect(model().attribute("currentMonth", MONTH + " " + YEAR))
                .andExpect(model().attribute("currentMonthValue", MONTH.name()))
                .andExpect(model().attribute("currentYearValue", YEAR));
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createBudget_firstBudget_shouldOnlyHaveDefaultIncomeGroup() throws Exception {
        mockMvc.perform(post("/budgets/create/{month}/{year}", MONTH, YEAR)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget created = budgetRepository
                .findAll().stream()
                .filter(b -> b.getMonth() == MONTH && b.getYear() == YEAR)
                .findFirst()
                .orElseThrow();

        List<Group> groups = groupRepository.findByBudgetsId(created.getId());
        assertThat(groups).hasSize(1);
        assertThat(groups.get(0).getName()).isEqualTo("Income");
        assertThat(groups.get(0).isDefault()).isTrue();

        List<BudgetCategory> budgetCategories = budgetCategoryRepository
                .findByBudgetId(created.getId());
        assertThat(budgetCategories).isEmpty();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createBudget_secondBudget_shouldLinkExistingGroupsToNewBudget() throws Exception {
        // Create first budget via API
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.JANUARY, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget firstBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.JANUARY && b.getYear() == 2025)
                .findFirst().orElseThrow();

        // Add a group via API — avoids manipulating detached lazy collections
        mockMvc.perform(post("/budgets/{budgetId}/groups", firstBudget.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Housing\"}"))
                .andExpect(status().isOk());

        Group housingGroup = groupRepository.findByBudgetsId(firstBudget.getId()).stream()
                .filter(g -> "Housing".equals(g.getName()))
                .findFirst().orElseThrow();

        long groupCountBefore = groupRepository.count();

        // Create second budget
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.FEBRUARY, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget secondBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.FEBRUARY && b.getYear() == 2025)
                .findFirst().orElseThrow();

        List<Group> secondGroups = groupRepository.findByBudgetsId(secondBudget.getId());

        // Correct groups are linked
        assertThat(secondGroups).hasSize(2);
        assertThat(secondGroups.stream().anyMatch(g -> g.getId().equals(housingGroup.getId()))).isTrue();
        assertThat(secondGroups.stream().anyMatch(g -> "Income".equals(g.getName()) && g.isDefault())).isTrue();

        // No new group rows were created — only new join table entries
        assertThat(groupRepository.count()).isEqualTo(groupCountBefore);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createBudget_secondBudget_shouldLinkExistingCategoriesToNewBudgetWithAllocations() throws Exception {
        // Create first budget via API
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.JANUARY, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget firstBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.JANUARY && b.getYear() == 2025)
                .findFirst().orElseThrow();

        // Add a group via API
        mockMvc.perform(post("/budgets/{budgetId}/groups", firstBudget.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Housing\"}"))
                .andExpect(status().isOk());

        Group housingGroup = groupRepository.findByBudgetsId(firstBudget.getId()).stream()
                .filter(g -> "Housing".equals(g.getName()))
                .findFirst().orElseThrow();

        // Add a category via API
        mockMvc.perform(post("/budgets/{budgetId}/groups/{groupId}/categories",
                        firstBudget.getId(), housingGroup.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"Rent\",\"allocation\":1200.00}"))
                .andExpect(status().isOk());

        Category rent = categoryRepository.findByGroupId(housingGroup.getId()).stream()
                .filter(c -> "Rent".equals(c.getName()))
                .findFirst().orElseThrow();

        long categoryCountBefore = categoryRepository.count();

        // Create second budget
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.FEBRUARY, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget secondBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.FEBRUARY && b.getYear() == 2025)
                .findFirst().orElseThrow();

        List<BudgetCategory> secondBcs = budgetCategoryRepository
                .findByBudgetId(secondBudget.getId());

        // Correct category is linked with correct allocation
        assertThat(secondBcs).hasSize(1);
        assertThat(secondBcs.get(0).getCategory().getId()).isEqualTo(rent.getId());
        assertThat(secondBcs.get(0).getAllocation()).isEqualByComparingTo("1200.00");

        // No new category rows were created — only a new BudgetCategory row
        assertThat(categoryRepository.count()).isEqualTo(categoryCountBefore);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createBudget_inheritsFromChronologicallyLastBudget_notEarliestOne() throws Exception {
        // Create January budget via API
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.JANUARY, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget januaryBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.JANUARY && b.getYear() == 2025)
                .findFirst().orElseThrow();

        // Add January Group via API
        mockMvc.perform(post("/budgets/{budgetId}/groups", januaryBudget.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"January Group\"}"))
                .andExpect(status().isOk());

        // Create March budget via API — inherits January Group from January budget
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.MARCH, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget marchBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.MARCH && b.getYear() == 2025)
                .findFirst().orElseThrow();

        // Add March Group via API
        mockMvc.perform(post("/budgets/{budgetId}/groups", marchBudget.getId())
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .content("{\"name\":\"March Group\"}"))
                .andExpect(status().isOk());

        // Create February budget — should inherit from March (the chronologically latest)
        mockMvc.perform(post("/budgets/create/{month}/{year}", Month.FEBRUARY, 2025)
                    .with(csrf()))
                .andExpect(status().is3xxRedirection());

        Budget februaryBudget = budgetRepository.findAll().stream()
                .filter(b -> b.getMonth() == Month.FEBRUARY && b.getYear() == 2025)
                .findFirst().orElseThrow();

        List<Group> februaryGroups = groupRepository.findByBudgetsId(februaryBudget.getId());

        // February inherits from March which itself inherited January Group and got March Group added
        assertThat(februaryGroups.stream()
                .anyMatch(g -> "March Group".equals(g.getName()))).isTrue();
        assertThat(februaryGroups.stream()
                .anyMatch(g -> "January Group".equals(g.getName()))).isTrue();
        assertThat(februaryGroups.stream()
                .anyMatch(g -> "Income".equals(g.getName()) && g.isDefault())).isTrue();
    }
}