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

    @Query("""
    SELECT b FROM Budget b
    LEFT JOIN FETCH b.groups g
    LEFT JOIN FETCH b.budgetCategories bc
    LEFT JOIN FETCH bc.category c
    LEFT JOIN FETCH c.group
    WHERE b.appUser.id = :userId
    AND b.month = :month
    AND b.year = :year
    ORDER BY LOWER(c.name) ASC
    """)
    Optional<Budget> findByAppUserIdAndMonthAndYearWithGroups(
        @Param("userId") Long userId,
        @Param("month") Month month,
        @Param("year") int year
    );

    @Query("""
    SELECT b FROM Budget b
    LEFT JOIN FETCH b.groups g
    LEFT JOIN FETCH b.budgetCategories bc
    LEFT JOIN FETCH bc.category c
    LEFT JOIN FETCH c.group
    WHERE b.appUser = :appUser
    ORDER BY b.year DESC,
             CASE b.month
                 WHEN 'DECEMBER'  THEN 12
                 WHEN 'NOVEMBER'  THEN 11
                 WHEN 'OCTOBER'   THEN 10
                 WHEN 'SEPTEMBER' THEN 9
                 WHEN 'AUGUST'    THEN 8
                 WHEN 'JULY'      THEN 7
                 WHEN 'JUNE'      THEN 6
                 WHEN 'MAY'       THEN 5
                 WHEN 'APRIL'     THEN 4
                 WHEN 'MARCH'     THEN 3
                 WHEN 'FEBRUARY'  THEN 2
                 WHEN 'JANUARY'   THEN 1
             END DESC
    LIMIT 1
    """)
    Optional<Budget> findLastBudgetByAppUser(@Param("appUser") AppUser appUser);

    @Query("""
    SELECT b FROM Budget b
    LEFT JOIN FETCH b.groups g
    LEFT JOIN FETCH b.budgetCategories bc
    LEFT JOIN FETCH bc.category c
    LEFT JOIN FETCH c.group
    WHERE b.id = :id
    """)
    Optional<Budget> findByIdWithGroupsAndCategories(@Param("id") Long id);
}
