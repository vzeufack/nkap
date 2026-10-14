/* nkap-transactions-page.js — Transactions page: details popup, table refresh, and the
   filter bar's client-side rules (custom range, dependent dropdowns, mutual exclusion, sort).
   The table is swapped by htmx when paging/filtering, so row and header click handlers are
   delegated from document rather than bound to each element. */

(function () {
    const PLACEHOLDER = '—';

    const DETAIL_FIELDS = {
        txDetailDate:        'date',
        txDetailDescription: 'description',
        txDetailDirection:   'direction',
        txDetailType:        'type',
        txDetailGroup:       'group',
        txDetailCategory:    'category',
        txDetailAccount:     'account',
        txDetailAccountType: 'accountType',
        txDetailBudget:      'budget',
        txDetailNote:        'note'
    };

    function toLabel(value) {
        return value.charAt(0) + value.slice(1).toLowerCase();
    }

    function showTransactionDetails(btn) {
        const data = btn.dataset;

        Object.entries(DETAIL_FIELDS).forEach(([elementId, key]) => {
            let value = data[key];
            if (value && (key === 'direction' || key === 'type')) {
                value = toLabel(value);
            }
            // textContent only — description and note are free-form user input.
            document.getElementById(elementId).textContent = value || PLACEHOLDER;
        });

        const amountEl = document.getElementById('txDetailAmount');
        amountEl.textContent = data.amount || PLACEHOLDER;
        amountEl.classList.toggle('credit', data.direction === 'CREDIT');
        amountEl.classList.toggle('debit',  data.direction === 'DEBIT');

        $('#transactionDetailsModal').modal('show');
    }

    // Add Transaction / Bulk Upload dispatch nkap:groupSaved on success — reload the
    // table at the current URL so the user stays on the page they were viewing.
    document.addEventListener('nkap:groupSaved', function () {
        htmx.ajax('GET', location.pathname + location.search, {
            target:  '#transactions-table-container',
            swap:    'innerHTML',
            headers: { 'HX-Request': 'true' }
        });
    });

    document.addEventListener('click', function (event) {
        const btn = event.target.closest('.tx-details-btn');
        if (btn) {
            showTransactionDetails(btn);
        }
    });

    // ── Row selection / bulk delete ──────────────────────────────────────────
    // Everything here lives inside the swapped table fragment, so look elements up on each
    // event; a swap (paging, filtering, refresh) naturally clears the selection.

    function selectedIds() {
        return Array.from(document.querySelectorAll('.tx-select:checked')).map(cb => Number.parseInt(cb.value, 10));
    }

    function syncSelection() {
        const boxes   = Array.from(document.querySelectorAll('.tx-select'));
        const checked = boxes.filter(cb => cb.checked);
        boxes.forEach(cb => cb.closest('tr').classList.toggle('selected', cb.checked));

        const selectAll = document.getElementById('txSelectAll');
        if (selectAll) {
            selectAll.checked       = boxes.length > 0 && checked.length === boxes.length;
            selectAll.indeterminate = checked.length > 0 && checked.length < boxes.length;
        }

        const bar = document.getElementById('txBulkBar');
        if (bar) {
            bar.hidden = checked.length === 0;
            document.getElementById('txBulkCount').textContent = checked.length + ' selected';
            document.getElementById('txBulkError').textContent = '';
        }
    }

    document.addEventListener('change', function (event) {
        if (event.target.id === 'txSelectAll') {
            document.querySelectorAll('.tx-select').forEach(cb => { cb.checked = event.target.checked; });
            syncSelection();
        } else if (event.target.classList.contains('tx-select')) {
            syncSelection();
        }
    });

    document.addEventListener('click', function (event) {
        if (event.target.closest('#btnBulkClear')) {
            document.querySelectorAll('.tx-select').forEach(cb => { cb.checked = false; });
            syncSelection();
            return;
        }
        if (!event.target.closest('#btnBulkDelete')) return;

        const ids = selectedIds();
        if (ids.length === 0) return;
        const noun = ids.length === 1 ? 'transaction' : 'transactions';
        if (!confirm('Delete ' + ids.length + ' ' + noun + '? This cannot be undone.')) return;

        Nkap.ui.setBusy('btnBulkDelete', 'btnBulkDeleteSpinner', 'btnBulkDeleteIcon', true);
        Nkap.http.postJson('/transactions/bulk-delete', 'POST', { ids: ids })
            .then(function () {
                Nkap.ui.dispatchGroupSaved(); // reloads the table, which clears the selection
            })
            .catch(function (err) {
                const error = document.getElementById('txBulkError');
                if (error) error.textContent = 'Could not delete the selected transactions. Please refresh and try again.';
                console.error('Error bulk deleting transactions:', err);
                Nkap.ui.setBusy('btnBulkDelete', 'btnBulkDeleteSpinner', 'btnBulkDeleteIcon', false);
            });
    });

    // ── Filter bar ──────────────────────────────────────────────────────────

    const form = document.getElementById('txFilterForm');
    if (!form) return;

    const el = id => document.getElementById(id);
    const period        = el('txFilterPeriod');
    const customRange   = el('txFilterCustomRange');
    const fromInput     = el('txFilterFrom');
    const toInput       = el('txFilterTo');
    const groupSel      = el('txFilterGroup');
    const categorySel   = el('txFilterCategory');
    const uncategorized = el('txFilterUncategorized');
    const typeSel       = el('txFilterAccountType');
    const accountSel    = el('txFilterAccount');
    const sortInput     = el('txFilterSort');

    /**
     * Shows only the options of `select` whose data attribute matches `value` (all of them when
     * value is empty), and clears the selection if it's no longer allowed.
     */
    function narrowOptions(select, dataKey, value) {
        Array.from(select.options).forEach(function (opt) {
            if (!opt.value) return; // keep the "All …" option
            const allowed = !value || opt.dataset[dataKey] === value;
            opt.hidden   = !allowed;
            opt.disabled = !allowed;
        });
        const selected = select.options[select.selectedIndex];
        if (selected && selected.disabled) select.value = '';
    }

    // Preset period vs custom range: the dates are only enabled (and so only submitted) for CUSTOM.
    function syncCustomRange() {
        const custom = period.value === 'CUSTOM';
        customRange.hidden = !custom;
        fromInput.disabled = !custom;
        toInput.disabled   = !custom;
        toInput.min   = fromInput.value || '';
        fromInput.max = toInput.value || '';
    }

    // Category vs "uncategorized only": each disables the other. Uncategorized also clears the
    // group, since uncategorized transactions have none.
    function syncCategoryRules() {
        if (uncategorized.checked) {
            groupSel.value = '';
            categorySel.value = '';
        }
        groupSel.disabled    = uncategorized.checked;
        categorySel.disabled = uncategorized.checked;
        narrowOptions(categorySel, 'groupId', groupSel.value);
        uncategorized.disabled = !!categorySel.value;
    }

    function syncAccountRules() {
        narrowOptions(accountSel, 'accountType', typeSel.value);
    }

    // These rules run in the capture phase so they've updated the form before htmx's
    // own change listener serializes it.
    form.addEventListener('change', function (event) {
        const target = event.target;
        if (target === period || target === fromInput || target === toInput) syncCustomRange();
        if (target === groupSel || target === categorySel || target === uncategorized) syncCategoryRules();
        if (target === typeSel) syncAccountRules();
    }, true);

    // Drop empty parameters so the pushed URL only carries active filters.
    form.addEventListener('htmx:configRequest', function (event) {
        const params = event.detail.parameters;
        Object.keys(params).forEach(function (key) {
            if (params[key] === '') delete params[key];
        });
        if (params.type === 'STANDARD') delete params.type;
        if (params.period === 'THIS_MONTH') delete params.period;
        if (params.sort === 'DATE_DESC') delete params.sort;
    });

    // Sortable headers are inside the swapped table, so delegate from document.
    document.addEventListener('click', function (event) {
        const toggle = event.target.closest('.tx-sort-toggle');
        if (!toggle) return;
        const desc = toggle.dataset.sortDesc;
        const asc  = toggle.dataset.sortAsc;
        // First click on a column sorts descending; clicking the active column flips it.
        sortInput.value = sortInput.value === desc ? asc : desc;
        htmx.trigger(form, 'submit');
    });

    // Restore consistent disabled/hidden states for a page loaded from a filtered URL.
    syncCustomRange();
    syncCategoryRules();
    syncAccountRules();
})();
