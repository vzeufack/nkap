package com.kmercoders.nkap.budget;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.kmercoders.nkap.appuser.AppUser;

import java.time.Month;
import java.util.List;
import java.util.Optional;

public interface BudgetRepository extends JpaRepository<Budget, Long> {
    List<Budget> findByAppUserId(Long appUserId);
    Optional<Budget> findByIdAndAppUserId(Long id, Long appUserId);
    Optional<Budget> findByAppUserIdAndMonthAndYear(Long appUserId, Month month, int year);
    boolean existsByAppUserAndMonthAndYear(AppUser appUser, Month month, int year);
    long countByAppUserAndMonthAndYear(AppUser appUser, Month month, int year);

    @Query("SELECT b FROM Budget b LEFT JOIN FETCH b.groups WHERE b.appUser.id = :userId AND b.month = :month AND b.year = :year")
    Optional<Budget> findByAppUserIdAndMonthAndYearWithGroups(
        @Param("userId") Long userId,
        @Param("month") Month month,
        @Param("year") int year
    );
}
