package com.kmercoders.nkap.budget;

import java.net.URI;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
public class BudgetController {
	private final BudgetRepository repository;

	public BudgetController(BudgetRepository repository) {
		this.repository = repository;
	}

	@GetMapping("/budgets")
	public Iterable<Budget> getBudgets() {
		return repository.findAll();
	}

	@GetMapping("/budgets/{year}/{month}")
	public ResponseEntity<Budget> getBudget(@PathVariable("year") Integer year, @PathVariable("month") Integer month) {
		Optional<Budget> found = repository.findByMonthAndYear(month, year);
		return found.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@GetMapping("/budgets/{id}")
	public ResponseEntity<Budget> getBudgetById(@PathVariable("id") Long id) {
		Optional<Budget> found = repository.findById(id);
		return found.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
	}

	@PostMapping("/budgets")
	public ResponseEntity<?> createBudget(@RequestBody Budget budget) {
		if (budget == null || budget.getMonth() == null || budget.getYear() == null) {
			return ResponseEntity.badRequest().body("month and year are required");
		}

		int month = budget.getMonth();
		if (month < 1 || month > 12) {
			return ResponseEntity.badRequest().body("month must be between 1 and 12");
		}

		if (repository.existsByMonthAndYear(budget.getMonth(), budget.getYear())) {
			return ResponseEntity.status(HttpStatus.CONFLICT).body("Budget for the given month and year already exists");
		}

		Budget saved = repository.save(budget);
		URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.getId()).toUri();
		return ResponseEntity.created(location).body(saved);
	}
}
