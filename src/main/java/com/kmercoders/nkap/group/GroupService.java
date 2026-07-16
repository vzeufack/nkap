package com.kmercoders.nkap.group;

import com.kmercoders.nkap.appuser.AppUser;
import com.kmercoders.nkap.appuser.AppUserService;
import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;
import com.kmercoders.nkap.category.BudgetCategoryRepository;

import org.hibernate.Hibernate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final BudgetRepository budgetRepository;
    private final BudgetCategoryRepository budgetCategoryRepository;
    private final AppUserService appUserService;

    public GroupService(GroupRepository groupRepository,
                         BudgetRepository budgetRepository,
                         BudgetCategoryRepository budgetCategoryRepository,
                         AppUserService appUserService) {
        this.groupRepository = groupRepository;
        this.budgetRepository = budgetRepository;
        this.budgetCategoryRepository = budgetCategoryRepository;
        this.appUserService = appUserService;
    }

    public List<Group> getGroupsByBudget(Long budgetId) {
        return groupRepository.findByBudgetsId(budgetId);
    }

    @Transactional
    public GroupDTO createGroup(Long budgetId, GroupDTO dto) {
        Budget budget = budgetRepository.findById(budgetId)
                .orElseThrow(() -> new IllegalArgumentException("Budget not found: " + budgetId));

        Group group = new Group(dto.getName());
        group.getBudgets().add(budget);
        budget.getGroups().add(group);

        Group savedGroup = groupRepository.save(group);
        return new GroupDTO(savedGroup.getId(), savedGroup.getName(), savedGroup.isDefault());
    }

    @Transactional
    public GroupDTO updateGroup(Long budgetId, Long groupId, GroupDTO dto) {
        Group group = groupRepository.findByIdAndBudgetsId(groupId, budgetId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found in this budget."));

        if (group.isDefault()) {
            throw new IllegalStateException("The default income group cannot be renamed.");
        }

        group.setName(dto.getName());
        Group saved = groupRepository.save(group);
        return new GroupDTO(saved.getId(), saved.getName(), saved.isDefault());
    }

    @Transactional
    public void deleteGroup(Long budgetId, Long groupId) {
        AppUser appUser = appUserService.getAuthenticatedUser();

        Budget budget = budgetRepository.findByIdAndAppUserId(budgetId, appUser.getId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Budget not found"));

        Group group = groupRepository.findByIdAndBudgetsId(groupId, budgetId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Group not found in this budget."));

        if (group.isDefault()) {
            throw new IllegalStateException("The default income group cannot be deleted.");
        }

        if (budgetCategoryRepository.existsByBudgetIdAndCategoryGroupId(budgetId, groupId)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "Cannot delete a group that has categories linked to it in this budget.");
        }

        budget.removeGroup(group);

        if (group.getBudgets().isEmpty()) {
            groupRepository.delete(group);
        }
    }
}