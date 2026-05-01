package com.kmercoders.nkap.budget;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Month;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByAppUserId(Long appUserId);
    Optional<Budget> findByIdAndAppUserId(Long id, Long appUserId);
    Optional<Budget> findByAppUserIdAndMonthAndYear(Long appUserId, Month month, int year);
}
