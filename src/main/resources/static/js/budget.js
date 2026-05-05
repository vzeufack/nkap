/* budget.js — nkap budgeting page */

$(document).ready(function () {

    // ── Derive current month/year from URL ──
    // Expected URL format: /budgets/{MONTH}/{YEAR}
    function getDateFromUrl() {
        const parts = window.location.pathname.split('/');
        // parts: ["", "budgets", "JANUARY", "2026"]
        const monthStr = parts[2];
        const yearStr  = parts[3];

        if (monthStr && yearStr) {
            const parsed = new Date(`${monthStr} 1, ${yearStr}`);
            if (!isNaN(parsed)) return parsed;
        }
        return new Date(); // fallback to today
    }

    // ── Month-only datepicker ──
    const $picker = $('#monthPicker');

    $picker.datepicker({
        format: "MM yyyy",
        startView: "months",
        minViewMode: "months",
        autoclose: true,
        todayHighlight: true
    });

    // Initialize picker from URL, not from today
    $picker.datepicker('setDate', getDateFromUrl());

    // Helper: get current picker date
    function getPickerDate() {
        return $picker.datepicker('getDate') || new Date();
    }

    // Helper: get full month name (e.g. "JANUARY")
    function getMonthName(date) {
        return date.toLocaleString('default', { month: 'long' }).toUpperCase();
    }

    // Helper: navigate to a given date's budget page
    function navigateTo(date) {
        const month = getMonthName(date);
        const year  = date.getFullYear();
        window.location.href = `/budgets/${month}/${year}`;
    }

    // Helper: set picker date without triggering navigation
    function setPickerDate(date) {
        $picker.datepicker('setDate', date);
    }

    // ── Previous month ──
    $('#btnPrevMonth').on('click', function () {
        const d = getPickerDate();
        d.setDate(1);
        d.setMonth(d.getMonth() - 1);
        navigateTo(d);
    });

    // ── Next month ──
    $('#btnNextMonth').on('click', function () {
        const d = getPickerDate();
        d.setDate(1);
        d.setMonth(d.getMonth() + 1);
        navigateTo(d);
    });

    // ── Today (current month) ──
    $('#btnToday').on('click', function () {
        navigateTo(new Date());
    });

    // ── Create Budget — POST to /budgets/create/{month}/{year} ──
    window.goToCreateBudget = function () {
        const d     = getPickerDate();
        const month = getMonthName(d);
        const year  = d.getFullYear();

        fetch(`/budgets/create/${month}/${year}`, { method: 'POST' })
            .then(response => {
                if (response.redirected) {
                    window.location.href = response.url;
                } else if (response.ok) {
                    window.location.href = `/budgets/${month}/${year}`;
                } else {
                    console.error('Failed to create budget:', response.status);
                }
            })
            .catch(err => console.error('Error creating budget:', err));
    };

    // ── React to manual datepicker selection only ──
    $picker.on('changeDate', function (e) {
        navigateTo(e.date);
    });
});