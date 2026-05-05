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
import org.springframework.security.test.context.support.WithUserDetails;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.Month;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class BudgetControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BudgetRepository budgetRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

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
}