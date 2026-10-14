package com.kmercoders.nkap.transaction;

import jakarta.persistence.criteria.Predicate;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.data.jpa.domain.Specification;

/** Dynamic queries for the transactions page. */
public final class TransactionSpecifications {

    private TransactionSpecifications() {}

    /**
     * Transactions owned by the user that match every active part of the filter, including its
     * transaction type (STANDARD by default).
     * The filter must already be {@link TransactionFilter#normalize() normalized}.
     */
    public static Specification<Transaction> forUser(Long appUserId, TransactionFilter filter, YearMonth now) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("budget").get("appUser").get("id"), appUserId));
            TransactionType type = filter.getType().toType();
            if (type != null) {
                predicates.add(cb.equal(root.get("transactionType"), type));
            }

            LocalDate from = filter.dateFrom(now);
            LocalDate to   = filter.dateTo(now);
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("transactionDate"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("transactionDate"), to));
            }

            if (filter.isUncategorized()) {
                predicates.add(cb.isNull(root.get("budgetCategory")));
            }
            if (filter.getGroupId() != null) {
                predicates.add(cb.equal(
                    root.get("budgetCategory").get("category").get("group").get("id"), filter.getGroupId()));
            }
            if (filter.getCategoryId() != null) {
                predicates.add(cb.equal(root.get("budgetCategory").get("category").get("id"), filter.getCategoryId()));
            }

            if (filter.getAccountType() != null) {
                predicates.add(cb.equal(root.get("account").get("accountType"), filter.getAccountType()));
            }
            if (filter.getAccountId() != null) {
                predicates.add(cb.equal(root.get("account").get("id"), filter.getAccountId()));
            }

            if (filter.getQ() != null) {
                String pattern = "%" + escapeLike(filter.getQ().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("description")), pattern, '\\'),
                    cb.like(cb.lower(root.get("note")), pattern, '\\')));
            }

            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Escapes LIKE wildcards so the keyword is matched literally. */
    private static String escapeLike(String value) {
        return value.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
