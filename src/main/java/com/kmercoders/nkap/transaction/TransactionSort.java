package com.kmercoders.nkap.transaction;

import org.springframework.data.domain.Sort;

/**
 * Sort orders for the transactions page. Amount sorts compare the stored amount, which is always
 * non-negative, so they order by size regardless of debit/credit.
 */
public enum TransactionSort {
    DATE_DESC(Sort.by(Sort.Order.desc("transactionDate"), Sort.Order.desc("id"))),
    DATE_ASC(Sort.by(Sort.Order.asc("transactionDate"), Sort.Order.asc("id"))),
    AMOUNT_DESC(Sort.by(Sort.Order.desc("amount"), Sort.Order.desc("transactionDate"), Sort.Order.desc("id"))),
    AMOUNT_ASC(Sort.by(Sort.Order.asc("amount"), Sort.Order.desc("transactionDate"), Sort.Order.desc("id")));

    private final Sort sort;

    TransactionSort(Sort sort) {
        this.sort = sort;
    }

    public Sort toSort() { return sort; }
}
