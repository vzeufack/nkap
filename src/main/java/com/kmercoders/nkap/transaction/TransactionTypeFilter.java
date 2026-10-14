package com.kmercoders.nkap.transaction;

/** Transaction-type choices for the transactions page filter: one {@link TransactionType}, or all of them. */
public enum TransactionTypeFilter {
    STANDARD("Standard", TransactionType.STANDARD),
    ADJUSTMENT("Adjustment", TransactionType.ADJUSTMENT),
    TRANSFER("Transfer", TransactionType.TRANSFER),
    ALL("All types", null);

    private final String label;
    private final TransactionType type;

    TransactionTypeFilter(String label, TransactionType type) {
        this.label = label;
        this.type  = type;
    }

    public String getLabel()         { return label; }

    /** The type to filter on, or null for {@link #ALL}. */
    public TransactionType toType()  { return type; }
}
