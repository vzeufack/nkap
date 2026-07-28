package com.kmercoders.nkap.financialinstitution;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FinancialInstitutionControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private FinancialInstitutionRepository financialInstitutionRepository;

    private static final String URL = "/financial-institutions";

    // ── List: Happy path ───────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "fi_user@example.com")
    void getFinancialInstitutions_returnsSeededDefaults() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.key=='BANK_OF_AMERICA')].displayName", is(java.util.List.of("Bank of America"))))
                .andExpect(jsonPath("$[?(@.key=='CAPITAL_ONE')].displayName", is(java.util.List.of("Capital One"))));
    }

    @Test
    @WithMockUser(username = "fi_user@example.com")
    void getFinancialInstitutions_seededKeysAreUnique() {
        long boaCount = financialInstitutionRepository.findAll().stream()
                .filter(fi -> fi.getKey().equals("BANK_OF_AMERICA"))
                .count();

        org.assertj.core.api.Assertions.assertThat(boaCount).isEqualTo(1);
    }

    // ── Security ───────────────────────────────────────────────────────────────

    @Test
    void getFinancialInstitutions_withoutAuthentication_returns401or302() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().is(anyOf(is(401), is(302))));
    }
}
