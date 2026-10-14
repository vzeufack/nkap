package com.kmercoders.nkap.transaction;

import com.kmercoders.nkap.account.AccountType;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.YearMonth;

import static org.assertj.core.api.Assertions.assertThat;

class TransactionFilterTest {

    private static final YearMonth NOW = YearMonth.of(2026, 10);

    private static TransactionFilter normalized(TransactionPeriod period) {
        TransactionFilter f = new TransactionFilter();
        f.setPeriod(period);
        return f.normalize();
    }

    // ── Date ranges ────────────────────────────────────────────────────────────

    @Test
    void defaultPeriod_isCurrentCalendarMonth() {
        TransactionFilter f = new TransactionFilter().normalize();
        assertThat(f.getPeriod()).isEqualTo(TransactionPeriod.THIS_MONTH);
        assertThat(f.dateFrom(NOW)).isEqualTo(LocalDate.of(2026, 10, 1));
        assertThat(f.dateTo(NOW)).isEqualTo(LocalDate.of(2026, 10, 31));
        assertThat(f.rangeLabel(NOW)).isEqualTo("October 2026");
    }

    @Test
    void pastNMonths_includeCurrentMonthAndNMinusOnePrevious() {
        assertThat(normalized(TransactionPeriod.PAST_3_MONTHS).dateFrom(NOW)).isEqualTo(LocalDate.of(2026, 8, 1));
        assertThat(normalized(TransactionPeriod.PAST_6_MONTHS).dateFrom(NOW)).isEqualTo(LocalDate.of(2026, 5, 1));
        assertThat(normalized(TransactionPeriod.PAST_9_MONTHS).dateFrom(NOW)).isEqualTo(LocalDate.of(2026, 2, 1));
        assertThat(normalized(TransactionPeriod.PAST_YEAR).dateFrom(NOW)).isEqualTo(LocalDate.of(2025, 11, 1));
        assertThat(normalized(TransactionPeriod.PAST_YEAR).dateTo(NOW)).isEqualTo(LocalDate.of(2026, 10, 31));
    }

    @Test
    void rangeLabels() {
        assertThat(normalized(TransactionPeriod.PAST_3_MONTHS).rangeLabel(NOW)).isEqualTo("Aug 1 – Oct 31, 2026");
        assertThat(normalized(TransactionPeriod.PAST_YEAR).rangeLabel(NOW)).isEqualTo("Nov 1, 2025 – Oct 31, 2026");
        assertThat(normalized(TransactionPeriod.FOREVER).rangeLabel(NOW)).isEqualTo("All time");
    }

    @Test
    void forever_hasNoBounds() {
        TransactionFilter f = normalized(TransactionPeriod.FOREVER);
        assertThat(f.dateFrom(NOW)).isNull();
        assertThat(f.dateTo(NOW)).isNull();
    }

    @Test
    void customRange_usesExplicitDatesAndAllowsOpenEnds() {
        TransactionFilter f = new TransactionFilter();
        f.setPeriod(TransactionPeriod.CUSTOM);
        f.setFrom(LocalDate.of(2025, 3, 10));
        f.normalize();
        assertThat(f.dateFrom(NOW)).isEqualTo(LocalDate.of(2025, 3, 10));
        assertThat(f.dateTo(NOW)).isNull();
        assertThat(f.rangeLabel(NOW)).isEqualTo("Since Mar 10, 2025");
    }

    // ── normalize() ────────────────────────────────────────────────────────────

    @Test
    void normalize_dropsCustomDatesForPresetPeriod() {
        TransactionFilter f = new TransactionFilter();
        f.setPeriod(TransactionPeriod.PAST_3_MONTHS);
        f.setFrom(LocalDate.of(2025, 1, 1));
        f.setTo(LocalDate.of(2025, 1, 31));
        f.normalize();
        assertThat(f.getFrom()).isNull();
        assertThat(f.getTo()).isNull();
    }

    @Test
    void normalize_swapsReversedCustomRange() {
        TransactionFilter f = new TransactionFilter();
        f.setPeriod(TransactionPeriod.CUSTOM);
        f.setFrom(LocalDate.of(2025, 3, 20));
        f.setTo(LocalDate.of(2025, 3, 10));
        f.normalize();
        assertThat(f.getFrom()).isEqualTo(LocalDate.of(2025, 3, 10));
        assertThat(f.getTo()).isEqualTo(LocalDate.of(2025, 3, 20));
    }

    @Test
    void normalize_uncategorizedClearsGroupAndCategory() {
        TransactionFilter f = new TransactionFilter();
        f.setUncategorized(true);
        f.setGroupId(1L);
        f.setCategoryId(2L);
        f.normalize();
        assertThat(f.getGroupId()).isNull();
        assertThat(f.getCategoryId()).isNull();
    }

    @Test
    void normalize_trimsSearchAndTurnsBlankIntoNull() {
        TransactionFilter f = new TransactionFilter();
        f.setQ("  coffee ");
        assertThat(f.normalize().getQ()).isEqualTo("coffee");

        TransactionFilter blank = new TransactionFilter();
        blank.setQ("   ");
        assertThat(blank.normalize().getQ()).isNull();
    }

    // ── Transaction type ───────────────────────────────────────────────────────

    @Test
    void type_defaultsToStandard() {
        assertThat(new TransactionFilter().normalize().getType()).isEqualTo(TransactionTypeFilter.STANDARD);
    }

    @Test
    void type_allMeansNoTypeRestriction() {
        assertThat(TransactionTypeFilter.ALL.toType()).isNull();
        assertThat(TransactionTypeFilter.TRANSFER.toType()).isEqualTo(TransactionType.TRANSFER);
    }

    @Test
    void type_nonDefaultIsInUrlAndNotDefaultView() {
        TransactionFilter f = new TransactionFilter();
        f.setType(TransactionTypeFilter.ALL);
        f.normalize();
        assertThat(f.pageUrl(0)).isEqualTo("/transactions?type=ALL&page=0");
        assertThat(f.isDefault()).isFalse();
    }

    // ── URLs / defaults ────────────────────────────────────────────────────────

    @Test
    void pageUrl_omitsDefaultsAndEmptyValues() {
        assertThat(new TransactionFilter().normalize().pageUrl(2)).isEqualTo("/transactions?page=2");
    }

    @Test
    void pageUrl_includesActiveFiltersAndEncodesSearch() {
        TransactionFilter f = new TransactionFilter();
        f.setPeriod(TransactionPeriod.CUSTOM);
        f.setFrom(LocalDate.of(2025, 3, 1));
        f.setAccountType(AccountType.CREDIT);
        f.setQ("café & tea");
        f.setSort(TransactionSort.AMOUNT_DESC);
        f.normalize();

        assertThat(f.pageUrl(0)).isEqualTo(
            "/transactions?period=CUSTOM&from=2025-03-01&accountType=CREDIT&q=caf%C3%A9%20%26%20tea&sort=AMOUNT_DESC&page=0");
    }

    @Test
    void isDefault_onlyForUnfilteredView() {
        assertThat(new TransactionFilter().normalize().isDefault()).isTrue();

        TransactionFilter sorted = new TransactionFilter();
        sorted.setSort(TransactionSort.DATE_ASC);
        assertThat(sorted.normalize().isDefault()).isFalse();
    }
}
