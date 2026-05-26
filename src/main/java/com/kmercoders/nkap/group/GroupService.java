package com.kmercoders.nkap.group;

import com.kmercoders.nkap.budget.Budget;
import com.kmercoders.nkap.budget.BudgetRepository;

import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final BudgetRepository budgetRepository;

    public GroupService(GroupRepository groupRepository, BudgetRepository budgetRepository) {
        this.groupRepository = groupRepository;
        this.budgetRepository = budgetRepository;
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
    public Group updateGroup(Long budgetId, Long groupId, GroupDTO dto) {
        Group group = groupRepository.findByIdAndBudgetsId(groupId, budgetId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found in this budget."));

        if (group.isDefault()) {
            throw new IllegalStateException("The default income group cannot be renamed.");
        }

        group.setName(dto.getName());
        return groupRepository.save(group);
    }

    @Transactional
    public void deleteGroup(Long budgetId, Long groupId) {
        Group group = groupRepository.findByIdAndBudgetsId(groupId, budgetId)
                .orElseThrow(() -> new IllegalArgumentException("Group not found in this budget."));

        if (group.isDefault()) {
            throw new IllegalStateException("The default income group cannot be deleted.");
        }

        groupRepository.delete(group);
    }
}