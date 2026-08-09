package com.kmercoders.nkap.category;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/budgets/{budgetId}/categories")
public class CategoryTransferController {

    private final CategoryService categoryService;

    public CategoryTransferController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @PostMapping("/transfer")
    public ResponseEntity<?> transfer(
            @PathVariable("budgetId") Long budgetId,
            @Valid @RequestBody CategoryTransferRequest request,
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

        return ResponseEntity.ok(categoryService.transferBalance(budgetId, request));
    }

    @PostMapping("/auto-allocate")
    public ResponseEntity<?> autoAllocate(@PathVariable("budgetId") Long budgetId) {
        return ResponseEntity.ok(categoryService.autoAllocateIncome(budgetId));
    }
}
