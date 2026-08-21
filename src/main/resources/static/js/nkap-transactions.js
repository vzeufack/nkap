/* nkap-transactions.js — Add/Edit/Delete Transaction modal logic.
 * Loaded persistently (see home.html) so it stays available across htmx fragment swaps.
 * Uses the shared Nkap.http / Nkap.forms / Nkap.ui helpers from nkap-core.js.
 */

function openAddTransactionModal() {
    document.getElementById('txAmountInput').value      = '';
    document.getElementById('txTypeSelect').value       = 'DEBIT';
    var _now = new Date();
    document.getElementById('txDateInput').value = _now.getFullYear() + '-'
        + String(_now.getMonth() + 1).padStart(2, '0') + '-'
        + String(_now.getDate()).padStart(2, '0');
    document.getElementById('txDescriptionInput').value = '';
    document.getElementById('txNoteInput').value        = '';
    var accountSel  = document.getElementById('txAccountSelect');
    var categorySel = document.getElementById('txCategorySelect');
    if (accountSel)  accountSel.value  = '';
    if (categorySel) categorySel.value = '';

    Nkap.forms.clearValidation(
        ['txAmountInput', 'txDateInput'],
        ['txAmountError', 'txDateError', 'txDescriptionError', 'txNoteError']
    );
    $('#addTransactionModal').modal('show');
}

function saveTransaction() {
    var amountInput    = document.getElementById('txAmountInput');
    var dateInput      = document.getElementById('txDateInput');
    var typeSelect     = document.getElementById('txTypeSelect');
    var noteInput      = document.getElementById('txNoteInput');
    var accountSelect  = document.getElementById('txAccountSelect');
    var categorySelect = document.getElementById('txCategorySelect');
    var budgetIdInput  = document.getElementById('txBudgetId');
    var amountError    = document.getElementById('txAmountError');
    var dateError      = document.getElementById('txDateError');

    Nkap.forms.clearValidation(['txAmountInput', 'txDateInput'], ['txAmountError', 'txDateError']);

    var amountRaw = amountInput.value.trim();
    var amount    = amountRaw === '' ? Number.NaN : Number.parseFloat(amountRaw);
    var valid     = true;

    if (Number.isNaN(amount) || amount < 0) {
        amountInput.classList.add('is-invalid');
        amountError.textContent = 'Please enter a valid amount (zero or positive).';
        valid = false;
    }
    if (!dateInput.value) {
        dateInput.classList.add('is-invalid');
        dateError.textContent = 'Please select a date.';
        valid = false;
    }
    if (!valid) return;

    Nkap.ui.setBusy('btnSaveTransaction', 'btnSaveTransactionSpinner', 'btnSaveTransactionIcon', true);

    var descriptionInput = document.getElementById('txDescriptionInput');
    var payload = {
        amount:          amount,
        transactionDate: dateInput.value,
        direction:       typeSelect.value,
        description:     descriptionInput.value.trim() || null,
        note:            noteInput.value.trim() || null,
        accountId:       accountSelect  && accountSelect.value  ? Number.parseInt(accountSelect.value, 10)  : null,
        categoryId:      categorySelect && categorySelect.value ? Number.parseInt(categorySelect.value, 10) : null,
        budgetId:        budgetIdInput  && budgetIdInput.value  ? Number.parseInt(budgetIdInput.value, 10)  : null
    };

    Nkap.http.postJson('/transactions', 'POST', payload)
        .then(function () {
            $('#addTransactionModal').modal('hide');
            Nkap.ui.dispatchGroupSaved();
        })
        .catch(function (err) {
            if (err && typeof err === 'object' && !err.message) {
                if (err.amount) {
                    amountInput.classList.add('is-invalid');
                    amountError.textContent = err.amount;
                }
                if (err.transactionDate) {
                    dateInput.classList.add('is-invalid');
                    dateError.textContent = err.transactionDate;
                }
                if (err.budgetId) {
                    dateInput.classList.add('is-invalid');
                    dateError.textContent = 'Please create a budget for this period first.';
                }
            } else if (err && err.status === 400 && err.message) {
                dateInput.classList.add('is-invalid');
                dateError.textContent = err.message;
            } else {
                amountInput.classList.add('is-invalid');
                amountError.textContent = 'Something went wrong. Please try again.';
            }
            console.error('Error saving transaction:', err);
        })
        .finally(function () {
            Nkap.ui.setBusy('btnSaveTransaction', 'btnSaveTransactionSpinner', 'btnSaveTransactionIcon', false);
        });
}

function openEditTransactionModal(btn) {
    document.getElementById('editTxId').value               = btn.dataset.id;
    document.getElementById('editTxBudgetId').value         = btn.dataset.budgetId;
    document.getElementById('editTxAmountInput').value      = btn.dataset.amount;
    document.getElementById('editTxTypeSelect').value       = btn.dataset.type;
    document.getElementById('editTxDateInput').value        = btn.dataset.date;
    document.getElementById('editTxDescriptionInput').value = btn.dataset.description || '';
    document.getElementById('editTxNoteInput').value        = btn.dataset.note || '';

    var accountSel = document.getElementById('editTxAccountSelect');
    if (accountSel) accountSel.value = btn.dataset.accountId || '';

    var srcSel  = document.getElementById('txCategorySelect');
    var destSel = document.getElementById('editTxCategorySelect');
    if (srcSel && destSel) {
        destSel.innerHTML = srcSel.innerHTML;
        destSel.value = btn.dataset.categoryId || '';
    }

    Nkap.forms.clearValidation(
        ['editTxAmountInput', 'editTxDateInput'],
        ['editTxAmountError', 'editTxDateError', 'editTxDescriptionError', 'editTxNoteError']
    );
    $('#editTransactionModal').modal('show');
}

function updateTransaction() {
    var amountInput    = document.getElementById('editTxAmountInput');
    var dateInput      = document.getElementById('editTxDateInput');
    var typeSelect     = document.getElementById('editTxTypeSelect');
    var noteInput      = document.getElementById('editTxNoteInput');
    var accountSelect  = document.getElementById('editTxAccountSelect');
    var categorySelect = document.getElementById('editTxCategorySelect');
    var budgetIdInput  = document.getElementById('editTxBudgetId');
    var amountError    = document.getElementById('editTxAmountError');
    var dateError      = document.getElementById('editTxDateError');

    Nkap.forms.clearValidation(['editTxAmountInput', 'editTxDateInput'], ['editTxAmountError', 'editTxDateError']);

    var amountRaw = amountInput.value.trim();
    var amount    = amountRaw === '' ? Number.NaN : Number.parseFloat(amountRaw);
    var valid     = true;

    if (Number.isNaN(amount) || amount < 0) {
        amountInput.classList.add('is-invalid');
        amountError.textContent = 'Please enter a valid amount (zero or positive).';
        valid = false;
    }
    if (!dateInput.value) {
        dateInput.classList.add('is-invalid');
        dateError.textContent = 'Please select a date.';
        valid = false;
    }
    if (!valid) return;

    Nkap.ui.setBusy('btnUpdateTransaction', 'btnUpdateTransactionSpinner', 'btnUpdateTransactionIcon', true);

    var descriptionInput = document.getElementById('editTxDescriptionInput');
    var id = document.getElementById('editTxId').value;
    var payload = {
        amount:          amount,
        transactionDate: dateInput.value,
        direction:       typeSelect.value,
        description:     descriptionInput.value.trim() || null,
        note:            noteInput.value.trim() || null,
        accountId:       accountSelect  && accountSelect.value  ? Number.parseInt(accountSelect.value, 10)  : null,
        categoryId:      categorySelect && categorySelect.value ? Number.parseInt(categorySelect.value, 10) : null,
        budgetId:        budgetIdInput  && budgetIdInput.value  ? Number.parseInt(budgetIdInput.value, 10)  : null
    };

    Nkap.http.postJson('/transactions/' + id, 'PUT', payload)
        .then(function () {
            $('#editTransactionModal').modal('hide');
            Nkap.ui.dispatchGroupSaved();
        })
        .catch(function (err) {
            if (err && typeof err === 'object' && !err.message) {
                if (err.amount) {
                    amountInput.classList.add('is-invalid');
                    amountError.textContent = err.amount;
                }
                if (err.transactionDate) {
                    dateInput.classList.add('is-invalid');
                    dateError.textContent = err.transactionDate;
                }
                if (err.budgetId) {
                    dateInput.classList.add('is-invalid');
                    dateError.textContent = 'Please create a budget for this period first.';
                }
            } else if (err && err.status === 400 && err.message) {
                dateInput.classList.add('is-invalid');
                dateError.textContent = err.message;
            } else {
                amountInput.classList.add('is-invalid');
                amountError.textContent = 'Something went wrong. Please try again.';
            }
            console.error('Error updating transaction:', err);
        })
        .finally(function () {
            Nkap.ui.setBusy('btnUpdateTransaction', 'btnUpdateTransactionSpinner', 'btnUpdateTransactionIcon', false);
        });
}

function deleteTransaction(btn) {
    if (!confirm('Delete this transaction? This cannot be undone.')) return;

    var id = btn.dataset.id;
    btn.disabled = true;

    Nkap.http.del('/transactions/' + id)
        .then(function () {
            Nkap.ui.dispatchGroupSaved();
        })
        .catch(function (err) {
            console.error('Error deleting transaction:', err);
            btn.disabled = false;
        });
}
