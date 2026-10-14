package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.AccountType;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Filter, search and sort state for the transactions page, bound from the query string so that
 * refreshes, bookmarks and history navigation keep it. Call {@link #normalize()} after binding.
 */
public class TransactionFilter {

    private static final DateTimeFormatter MONTH_FORMAT     = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_FORMAT       = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH);
    private static final DateTimeFormatter DAY_YEAR_FORMAT  = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH);

    private TransactionTypeFilter type;
    private TransactionPeriod period;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;

    private Long groupId;
    private Long categoryId;
    private boolean uncategorized;
    private AccountType accountType;
    private Long accountId;
    private String q;
    private TransactionSort sort;

    /**
     * Applies defaults and the mutually exclusive rules: a preset period ignores custom dates,
     * "uncategorized" ignores group and category, and a reversed custom range is swapped.
     */
    public TransactionFilter normalize() {
        if (type == null) {
            type = TransactionTypeFilter.STANDARD;
        }
        if (period == null) {
            period = TransactionPeriod.THIS_MONTH;
        }
        if (sort == null) {
            sort = TransactionSort.DATE_DESC;
        }
        if (period != TransactionPeriod.CUSTOM) {
            from = null;
            to   = null;
        } else if (from != null && to != null && from.isAfter(to)) {
            LocalDate swap = from;
            from = to;
            to   = swap;
        }
        if (uncategorized) {
            groupId    = null;
            categoryId = null;
        }
        if (q != null) {
            q = q.trim();
            if (q.isEmpty()) {
                q = null;
            }
        }
        return this;
    }

    /** Inclusive lower date bound, or null for none. */
    public LocalDate dateFrom(YearMonth now) {
        return switch (period) {
            case CUSTOM  -> from;
            case FOREVER -> null;
            default      -> now.minusMonths(period.getMonths() - 1L).atDay(1);
        };
    }

    /** Inclusive upper date bound, or null for none. */
    public LocalDate dateTo(YearMonth now) {
        return switch (period) {
            case CUSTOM  -> to;
            case FOREVER -> null;
            default      -> now.atEndOfMonth();
        };
    }

    /** Human-readable date range, e.g. "October 2026", "Aug 1 – Oct 31, 2026" or "All time". */
    public String rangeLabel(YearMonth now) {
        if (period == TransactionPeriod.THIS_MONTH) {
            return now.format(MONTH_FORMAT);
        }
        LocalDate start = dateFrom(now);
        LocalDate end   = dateTo(now);
        if (start == null && end == null) {
            return "All time";
        }
        if (start == null) {
            return "Up to " + end.format(DAY_YEAR_FORMAT);
        }
        if (end == null) {
            return "Since " + start.format(DAY_YEAR_FORMAT);
        }
        String startText = start.getYear() == end.getYear() ? start.format(DAY_FORMAT) : start.format(DAY_YEAR_FORMAT);
        return startText + " – " + end.format(DAY_YEAR_FORMAT);
    }

    /** True when nothing differs from the default view (standard transactions this month, no filters, newest first). */
    public boolean isDefault() {
        return type == TransactionTypeFilter.STANDARD
            && period == TransactionPeriod.THIS_MONTH
            && groupId == null && categoryId == null && !uncategorized
            && accountType == null && accountId == null
            && q == null
            && sort == TransactionSort.DATE_DESC;
    }

    /** URL of the given page with every active filter; defaults and empty values are left out. */
    public String pageUrl(int page) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/transactions");
        if (type != TransactionTypeFilter.STANDARD) builder.queryParam("type", type.name());
        if (period != TransactionPeriod.THIS_MONTH) builder.queryParam("period", period.name());
        if (from != null)                           builder.queryParam("from", from);
        if (to != null)                             builder.queryParam("to", to);
        if (groupId != null)                        builder.queryParam("groupId", groupId);
        if (categoryId != null)                     builder.queryParam("categoryId", categoryId);
        if (uncategorized)                          builder.queryParam("uncategorized", true);
        if (accountType != null)                    builder.queryParam("accountType", accountType.name());
        if (accountId != null)                      builder.queryParam("accountId", accountId);
        if (q != null)                              builder.queryParam("q", q);
        if (sort != TransactionSort.DATE_DESC)      builder.queryParam("sort", sort.name());
        builder.queryParam("page", page);
        return builder.encode().build().toUriString();
    }

    public TransactionTypeFilter getType()               { return type; }
    public void setType(TransactionTypeFilter type)      { this.type = type; }
    public TransactionPeriod getPeriod()                 { return period; }
    public void setPeriod(TransactionPeriod period)      { this.period = period; }
    public LocalDate getFrom()                           { return from; }
    public void setFrom(LocalDate from)                  { this.from = from; }
    public LocalDate getTo()                             { return to; }
    public void setTo(LocalDate to)                      { this.to = to; }
    public Long getGroupId()                             { return groupId; }
    public void setGroupId(Long groupId)                 { this.groupId = groupId; }
    public Long getCategoryId()                          { return categoryId; }
    public void setCategoryId(Long categoryId)           { this.categoryId = categoryId; }
    public boolean isUncategorized()                     { return uncategorized; }
    public void setUncategorized(boolean uncategorized)  { this.uncategorized = uncategorized; }
    public AccountType getAccountType()                  { return accountType; }
    public void setAccountType(AccountType accountType)  { this.accountType = accountType; }
    public Long getAccountId()                           { return accountId; }
    public void setAccountId(Long accountId)             { this.accountId = accountId; }
    public String getQ()                                 { return q; }
    public void setQ(String q)                           { this.q = q; }
    public TransactionSort getSort()                     { return sort; }
    public void setSort(TransactionSort sort)            { this.sort = sort; }
}
