package com.kmercoders.nkap.category;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/budgets/{budgetId}/groups/{groupId}/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ResponseEntity<List<CategoryDTO>> getCategories(
            @PathVariable Long budgetId,
            @PathVariable Long groupId) {

        return ResponseEntity.ok(categoryService.getCategoriesForBudget(budgetId, groupId));
    }

    @PostMapping
    public ResponseEntity<?> createCategory(
            @PathVariable("budgetId") Long budgetId,
            @PathVariable("groupId") Long groupId,
            @Valid @RequestBody CategoryRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            Map<String, String> errors = bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(
                    fe -> fe.getField(),
                    fe -> fe.getDefaultMessage(),
                    (first, second) -> first
                ));
            return ResponseEntity.badRequest().body(errors);
        }

        CategoryDTO created = categoryService.createCategory(budgetId, groupId, request);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{categoryId}")
    public ResponseEntity<?> updateCategory(
            @PathVariable("budgetId") Long budgetId,
            @PathVariable("groupId") Long groupId,
            @PathVariable("categoryId") Long categoryId,
            @Valid @RequestBody CategoryRequest request,
            BindingResult bindingResult) {

        if (bindingResult.hasErrors()) {
            Map<String, String> errors = bindingResult.getFieldErrors().stream()
                .collect(Collectors.toMap(
                    fe -> fe.getField(),
                    fe -> fe.getDefaultMessage(),
                    (first, second) -> first
                ));
            return ResponseEntity.badRequest().body(errors);
        }

        CategoryDTO updated = categoryService.updateCategory(budgetId, groupId, categoryId, request);
        return ResponseEntity.ok(updated);
    }
}
