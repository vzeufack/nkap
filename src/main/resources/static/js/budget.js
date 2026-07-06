/* budget.js — nkap budgeting page */

$(document).ready(function () {

    // ── Derive current month/year from URL ──
    function getDateFromUrl() {
        const parts = window.location.pathname.split('/');
        const monthStr = parts[2];
        const yearStr  = parts[3];

        if (monthStr && yearStr) {
            const parsed = new Date(`${monthStr} 1, ${yearStr}`);
            if (!isNaN(parsed)) return parsed;
        }
        return new Date();
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

    $picker.datepicker('setDate', getDateFromUrl());

    function getPickerDate() {
        return $picker.datepicker('getDate') || new Date();
    }

    function getMonthName(date) {
        return date.toLocaleString('default', { month: 'long' }).toUpperCase();
    }

    // ── Core navigation — HTMX swap instead of full reload ──
    function navigateTo(date) {
        const month = getMonthName(date);
        const year  = date.getFullYear();

        // Update the picker display without triggering changeDate again
        $picker.off('changeDate');
        $picker.datepicker('setDate', date);
        $picker.on('changeDate', onChangeDate);

        // Update the browser URL without reloading
        history.pushState(null, '', `/budgets/${month}/${year}`);

        // Swap only the fragment
        htmx.ajax('GET', `/budgets/${month}/${year}`, {
            target: '#budget-plan-container',
            swap:   'innerHTML',
            headers: { 'HX-Request': 'true' }
        });
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

    // ── Today ──
    $('#btnToday').on('click', function () {
        navigateTo(new Date());
    });

    // ── Datepicker manual selection ──
    function onChangeDate(e) {
        navigateTo(e.date);
    }
    $picker.on('changeDate', onChangeDate);

    // ── Create Budget ──
    window.goToCreateBudget = function () {
        const d     = getPickerDate();
        const month = getMonthName(d);
        const year  = d.getFullYear();

        fetch(`/budgets/create/${month}/${year}`, { method: 'POST' })
            .then(response => {
                if (response.redirected || response.ok) {
                    // After creation, swap in the new fragment
                    navigateTo(d);
                } else {
                    console.error('Failed to create budget:', response.status);
                }
            })
            .catch(err => console.error('Error creating budget:', err));
    };

    // ── Handle browser back/forward buttons ──
    window.addEventListener('popstate', function () {
        navigateTo(getDateFromUrl());
    });

    // Refresh fragment after a group/category is saved
    document.addEventListener('nkap:groupSaved', function() {
        navigateTo(getPickerDate());
    });

    // Keep the transaction modal's category dropdown in sync with the budget fragment
    document.addEventListener('htmx:afterSettle', function() {
        rebuildCategoryDropdown();
    });

    function rebuildCategoryDropdown() {
        const sel = document.getElementById('txCategorySelect');
        if (!sel) return;

        const currentVal = sel.value;

        while (sel.options.length > 1) sel.remove(1);

        document.querySelectorAll('#budget-plan-container .group-card').forEach(function(card) {
            const nameEl = card.querySelector('.group-card-name');
            const rows   = card.querySelectorAll('.cat-row[data-category-id]');
            if (!nameEl || rows.length === 0) return;

            const optgroup  = document.createElement('optgroup');
            optgroup.label = nameEl.textContent.trim();

            rows.forEach(function(row) {
                const catId   = row.dataset.categoryId;
                const catName = row.querySelector('.cat-name');
                if (!catId || !catName) return;

                const opt = document.createElement('option');
                opt.value       = catId;
                opt.textContent = catName.textContent.trim();
                optgroup.appendChild(opt);
            });

            sel.appendChild(optgroup);
        });

        if (currentVal) sel.value = currentVal;
    }

});