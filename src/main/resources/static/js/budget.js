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
        refreshAccountBalances();
    });

    // Transactions can change account balances, but the accounts sidebar lives
    // outside #budget-plan-container, so htmx's fragment swap never touches it.
    function refreshAccountBalances() {
        fetch('/accounts')
            .then(function(response) { return response.ok ? response.json() : Promise.reject(response); })
            .then(function(accounts) {
                var netWorth = 0;

                accounts.forEach(function(account) {
                    netWorth += account.balance;

                    var item = document.querySelector('.account-item[data-account-id="' + account.id + '"]');
                    if (!item) return;

                    var balanceEl = item.querySelector('.account-balance');
                    if (balanceEl) {
                        balanceEl.textContent = formatCurrency(account.balance);
                        balanceEl.classList.toggle('negative', account.balance < 0);
                    }

                    var editBtn = item.querySelector('.account-edit-btn');
                    if (editBtn) editBtn.dataset.balance = account.balance;
                });

                var totalEl = document.querySelector('.accounts-total .total-amount');
                if (totalEl) totalEl.textContent = formatCurrency(netWorth);
            })
            .catch(function(err) { console.error('Error refreshing account balances:', err); });
    }

    function formatCurrency(amount) {
        return '$' + amount.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 });
    }

    // Keep the transaction modal's category dropdown and budget ID in sync with the budget fragment
    document.addEventListener('htmx:afterSettle', function() {
        rebuildCategoryDropdown();
        syncBudgetId();
    });

    function syncBudgetId() {
        const budgetIdInput = document.getElementById('txBudgetId');
        if (!budgetIdInput) return;
        const budgetContent = document.querySelector('[data-budget-id]');
        budgetIdInput.value = budgetContent ? budgetContent.dataset.budgetId : '';

        const addTransactionBtn = document.getElementById('btnAddTransaction');
        if (addTransactionBtn) {
            addTransactionBtn.classList.toggle('d-none', !budgetContent);
        }
    }

    function rebuildCategoryDropdown() {
        const sel = document.getElementById('txCategorySelect');
        if (!sel) return;

        const currentVal = sel.value;

        while (sel.children.length > 1) sel.lastChild.remove();

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