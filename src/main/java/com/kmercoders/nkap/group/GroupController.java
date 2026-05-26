package com.kmercoders.nkap.group;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/budgets")
public class GroupController {

    private final GroupService groupService;

    public GroupController(GroupService groupService) {
        this.groupService = groupService;
    }

    @GetMapping("/{budgetId}/groups")
    public ResponseEntity<List<Group>> getGroupsByBudget(@PathVariable Long budgetId) {
        return ResponseEntity.ok(groupService.getGroupsByBudget(budgetId));
    }

    @PostMapping("/{budgetId}/groups")
    public ResponseEntity<GroupDTO> createGroup(@PathVariable("budgetId") Long budgetId, @RequestBody GroupDTO dto) {
        return ResponseEntity.ok(groupService.createGroup(budgetId, dto));
    }

    @PutMapping("/{budgetId}/groups/{groupId}")
    public ResponseEntity<Group> updateGroup(@PathVariable("budgetId") Long budgetId,
                                             @PathVariable("groupId") Long groupId,
                                             @RequestBody GroupDTO dto) {
        return ResponseEntity.ok(groupService.updateGroup(budgetId, groupId, dto));
    }

    @DeleteMapping("/{budgetId}/groups/{groupId}")
    public ResponseEntity<Void> deleteGroup(@PathVariable Long budgetId,
                                            @PathVariable Long groupId) {
        groupService.deleteGroup(budgetId, groupId);
        return ResponseEntity.noContent().build();
    }
}