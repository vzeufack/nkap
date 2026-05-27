package com.kmercoders.nkap.group;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserRepository;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.budget.BudgetService;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.time.Month;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private GroupRepository groupRepository;
    @Autowired private BudgetRepository budgetRepository;
    @Autowired private BudgetService budgetService;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private ObjectMapper objectMapper;

    private static final String EMAIL = "group_user@example.com";

    private Budget budget;

    @BeforeAll
    void createUser() {
        appUserRepository.deleteAll();
        AppUser user = new AppUser(EMAIL, passwordEncoder.encode("somepassword"));
        appUserRepository.save(user);
    }

    @BeforeEach
    void setUp() {
        budgetRepository.deleteAll();
        groupRepository.deleteAll();

        AppUser user = appUserRepository.findByEmail(EMAIL).orElseThrow();
        budget = budgetService.createBudget(user, Month.JUNE, 2025);
    }

    @Test
    @WithMockUser(username = EMAIL)
    void createGroup_shouldSaveGroupAndReturnIt_whenValidRequest() throws Exception {
        GroupDTO dto = new GroupDTO("Housing");

        mockMvc.perform(post("/budgets/{budgetId}/groups", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Housing"))
                .andExpect(jsonPath("$.default").value(false));

        List<Group> groups = groupRepository.findByBudgetsId(budget.getId());

        assertThat(groups).hasSize(2);

        Group housingGroup = groups.stream()
                .filter(g -> "Housing".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Housing group not found in budget"));

        assertThat(housingGroup.isDefault()).isFalse();

        Group incomeGroup = groups.stream()
                .filter(g -> "Income".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Default income group not found in budget"));

        assertThat(incomeGroup.isDefault()).isTrue();
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateGroup_shouldUpdateGroupName_whenGroupIsNotDefault() throws Exception {
        // Create a non-default group to update
        GroupDTO createDto = new GroupDTO("Housing");
        mockMvc.perform(post("/budgets/{budgetId}/groups", budget.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createDto)))
                .andExpect(status().isOk());

        Group housingGroup = groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(g -> "Housing".equals(g.getName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Housing group not found"));

        GroupDTO updateDto = new GroupDTO("Rent");

        mockMvc.perform(put("/budgets/{budgetId}/groups/{groupId}", budget.getId(), housingGroup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Rent"))
                .andExpect(jsonPath("$.default").value(false));

        Group updatedGroup = groupRepository.findById(housingGroup.getId()).orElseThrow();
        assertThat(updatedGroup.getName()).isEqualTo("Rent");
    }

    @Test
    @WithMockUser(username = EMAIL)
    void updateGroup_shouldReturn403_whenGroupIsDefault() throws Exception {
        Group incomeGroup = groupRepository.findByBudgetsId(budget.getId()).stream()
                .filter(Group::isDefault)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Default income group not found"));

        GroupDTO updateDto = new GroupDTO("Renamed Income");

        mockMvc.perform(put("/budgets/{budgetId}/groups/{groupId}", budget.getId(), incomeGroup.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateDto)))
                .andExpect(status().isForbidden())
                .andExpect(content().string("The default income group cannot be renamed."));

        Group unchangedGroup = groupRepository.findById(incomeGroup.getId()).orElseThrow();
        assertThat(unchangedGroup.getName()).isEqualTo("Income");
    }
}