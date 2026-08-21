/* nkap-transfers.js — Transfer Balance modal + auto-allocate-income logic.
 * Loaded persistently (see home.html) so it stays available across htmx fragment swaps
 * (autoAllocateIncome is invoked from budget-plan.html's markup, which is re-rendered
 * on every month navigation, but the function itself must be defined exactly once).
 * Uses the shared Nkap.http / Nkap.forms / Nkap.ui helpers from nkap-core.js.
 */

function updateTransferSourceAvailable() {
    var sourceSelect  = document.getElementById('transferSourceSelect');
    var availableHint = document.getElementById('transferSourceAvailable');
    var selectedOpt   = sourceSelect.options[sourceSelect.selectedIndex];
    var balance       = selectedOpt ? Number.parseFloat(selectedOpt.dataset.balance || '0') : 0;
    availableHint.textContent = '$' + balance.toFixed(2) + ' available';
}

function openTransferModal(categoryId) {
    var sourceSelect = document.getElementById('transferSourceSelect');
    var targetSelect = document.getElementById('transferTargetSelect');
    var amountInput  = document.getElementById('transferAmountInput');

    Nkap.ui.buildCategoryOptions(sourceSelect, { clearAll: true, includeBalance: true });
    Nkap.ui.buildCategoryOptions(targetSelect, { clearAll: true, includeBalance: true });

    sourceSelect.value = categoryId;
    targetSelect.value = '';
    amountInput.value  = '';

    Nkap.forms.clearValidation(
        ['transferSourceSelect', 'transferTargetSelect', 'transferAmountInput'],
        ['transferSourceError', 'transferTargetError', 'transferAmountError', 'transferFormError']
    );

    updateTransferSourceAvailable();
    sourceSelect.onchange = updateTransferSourceAvailable;

    $('#transferBalanceModal').modal('show');
}

function saveTransfer() {
    var sourceSelect = document.getElementById('transferSourceSelect');
    var targetSelect = document.getElementById('transferTargetSelect');
    var amountInput  = document.getElementById('transferAmountInput');

    var sourceError = document.getElementById('transferSourceError');
    var targetError = document.getElementById('transferTargetError');
    var amountError = document.getElementById('transferAmountError');
    var formError    = document.getElementById('transferFormError');

    Nkap.forms.clearValidation(
        ['transferSourceSelect', 'transferTargetSelect', 'transferAmountInput'],
        ['transferSourceError', 'transferTargetError', 'transferAmountError', 'transferFormError']
    );

    var sourceId = sourceSelect.value;
    var targetId = targetSelect.value;
    var amount   = Number.parseFloat(amountInput.value);
    var valid    = true;

    if (!sourceId) {
        sourceSelect.classList.add('is-invalid');
        sourceError.textContent = 'Please select a source category.';
        valid = false;
    }
    if (!targetId) {
        targetSelect.classList.add('is-invalid');
        targetError.textContent = 'Please select a target category.';
        valid = false;
    }
    if (sourceId && targetId && sourceId === targetId) {
        targetSelect.classList.add('is-invalid');
        targetError.textContent = 'Source and target categories must be different.';
        valid = false;
    }
    if (Number.isNaN(amount) || amount <= 0) {
        amountInput.classList.add('is-invalid');
        amountError.textContent = 'Please enter a valid amount greater than zero.';
        valid = false;
    } else {
        var selectedOpt = sourceSelect.options[sourceSelect.selectedIndex];
        var available   = selectedOpt ? Number.parseFloat(selectedOpt.dataset.balance || '0') : 0;
        if (amount > available) {
            amountInput.classList.add('is-invalid');
            amountError.textContent = 'Only $' + available.toFixed(2) + ' is available to transfer.';
            valid = false;
        }
    }
    if (!valid) return;

    var budgetId = Nkap.ui.getBudgetId();
    if (!budgetId) { console.error('No budget id found'); return; }

    Nkap.ui.setBusy('btnSaveTransfer', 'btnSaveTransferSpinner', 'btnSaveTransferIcon', true);

    var payload = {
        sourceCategoryId: Number.parseInt(sourceId, 10),
        targetCategoryId: Number.parseInt(targetId, 10),
        amount:           amount
    };

    Nkap.http.postJson('/budgets/' + budgetId + '/categories/transfer', 'POST', payload)
        .then(function () {
            $('#transferBalanceModal').modal('hide');
            Nkap.ui.dispatchGroupSaved();
        })
        .catch(function (err) {
            if (err && typeof err === 'object' && !err.message) {
                if (err.sourceCategoryId) {
                    sourceSelect.classList.add('is-invalid');
                    sourceError.textContent = err.sourceCategoryId;
                }
                if (err.targetCategoryId) {
                    targetSelect.classList.add('is-invalid');
                    targetError.textContent = err.targetCategoryId;
                }
                if (err.amount) {
                    amountInput.classList.add('is-invalid');
                    amountError.textContent = err.amount;
                }
            } else if (err && err.status === 400 && err.message) {
                formError.textContent = err.message;
            } else {
                formError.textContent = 'Something went wrong. Please try again.';
            }
            console.error('Error transferring balance:', err);
        })
        .finally(function () {
            Nkap.ui.setBusy('btnSaveTransfer', 'btnSaveTransferSpinner', 'btnSaveTransferIcon', false);
        });
}

// ── Auto-allocate available income to underfunded expense categories ──
function autoAllocateIncome() {
    if (!confirm('Allocate your available income to underfunded categories?')) return;

    var budgetId = Nkap.ui.getBudgetId();
    if (!budgetId) { console.error('No budget id found'); return; }

    Nkap.ui.setBusy('btnAutoAllocate', 'btnAutoAllocateSpinner', 'btnAutoAllocateIcon', true);

    Nkap.http.postJson('/budgets/' + budgetId + '/categories/auto-allocate', 'POST', undefined)
        .then(function (result) {
            if (result.transfersCreated > 0) {
                alert('Allocated $' + Number(result.totalAllocated).toFixed(2)
                    + ' across ' + result.transfersCreated + ' categor' + (result.transfersCreated === 1 ? 'y' : 'ies') + '.');
                Nkap.ui.dispatchGroupSaved();
            } else {
                alert('Nothing to allocate right now.');
            }
        })
        .catch(function (err) {
            alert(err && err.message ? err.message : 'Something went wrong. Please try again.');
            console.error('Error auto-allocating income:', err);
        })
        .finally(function () {
            Nkap.ui.setBusy('btnAutoAllocate', 'btnAutoAllocateSpinner', 'btnAutoAllocateIcon', false);
        });
}
