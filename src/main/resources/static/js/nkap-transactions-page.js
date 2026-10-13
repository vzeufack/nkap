/* nkap-transactions-page.js — Transactions page: details popup.
   The table is swapped by htmx when paging, so the click handler is delegated
   from document rather than bound to each row's button. */

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

    document.addEventListener('click', function (event) {
        const btn = event.target.closest('.tx-details-btn');
        if (btn) {
            showTransactionDetails(btn);
        }
    });
})();
