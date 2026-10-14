package com.kmercoders.nkap.transaction;

/**
 * Predefined date ranges for the transactions page, measured in calendar months ending with the
 * current month: "Past 3 months" in October is Aug 1 – Oct 31. {@link #CUSTOM} uses an explicit
 * from/to range instead, and {@link #FOREVER} has no bounds.
 */
public enum TransactionPeriod {
    THIS_MONTH("Last month", 1),
    PAST_3_MONTHS("Past 3 months", 3),
    PAST_6_MONTHS("Past 6 months", 6),
    PAST_9_MONTHS("Past 9 months", 9),
    PAST_YEAR("Past year", 12),
    FOREVER("Forever", 0),
    CUSTOM("Custom range…", 0);

    private final String label;
    private final int months;

    TransactionPeriod(String label, int months) {
        this.label  = label;
        this.months = months;
    }

    public String getLabel() { return label; }

    /** Number of calendar months covered, counting the current one; 0 for FOREVER and CUSTOM. */
    public int getMonths()   { return months; }
}
