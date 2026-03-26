package com.kmercoders.nkap.budget;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, Long> {
    Optional<Budget> findByMonthAndYear(Integer month, Integer year);

    boolean existsByMonthAndYear(Integer month, Integer year);
}
