package com.kmercoders.nkap.transaction;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    @PostMapping
    public ResponseEntity<?> createTransaction(@Valid @RequestBody TransactionRequest request,
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

        return ResponseEntity.ok(transactionService.createTransaction(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateTransaction(@PathVariable("id") Long id,
                                               @Valid @RequestBody TransactionRequest request,
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

        return ResponseEntity.ok(transactionService.updateTransaction(id, request));
    }
}
