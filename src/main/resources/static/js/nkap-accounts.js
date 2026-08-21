/* nkap-accounts.js — Add/Edit/Delete Account modal logic.
 * Loaded persistently (see home.html) so it stays available across htmx fragment swaps.
 * Uses the shared Nkap.http / Nkap.forms / Nkap.ui helpers from nkap-core.js.
 */

function openEditAccountModal(btn) {
    document.getElementById('editAccountId').value             = btn.dataset.id;
    document.getElementById('editAccountNameInput').value      = btn.dataset.name;
    document.getElementById('editAccountTypeSelect').value     = btn.dataset.type;
    document.getElementById('editAccountFinancialInstitutionSelect').value = btn.dataset.financialInstitutionId || '';
    document.getElementById('editAccountBalanceInput').value   = btn.dataset.balance;

    Nkap.forms.clearValidation(
        ['editAccountNameInput', 'editAccountTypeSelect', 'editAccountFinancialInstitutionSelect', 'editAccountBalanceInput'],
        ['editAccountNameError', 'editAccountTypeError', 'editAccountFinancialInstitutionError', 'editAccountBalanceError']
    );
    $('#editAccountModal').modal('show');
}

function updateAccount() {
    var nameInput    = document.getElementById('editAccountNameInput');
    var typeSelect   = document.getElementById('editAccountTypeSelect');
    var fiSelect     = document.getElementById('editAccountFinancialInstitutionSelect');
    var balanceInput = document.getElementById('editAccountBalanceInput');
    var nameError    = document.getElementById('editAccountNameError');
    var balanceError = document.getElementById('editAccountBalanceError');

    Nkap.forms.clearValidation(['editAccountNameInput', 'editAccountBalanceInput'], ['editAccountNameError', 'editAccountBalanceError']);

    var name                   = nameInput.value.trim();
    var type                   = typeSelect.value;
    var financialInstitutionId = fiSelect.value ? Number.parseInt(fiSelect.value, 10) : null;
    var balanceRaw             = balanceInput.value.trim();
    var balance                = balanceRaw === '' ? 0 : Number.parseFloat(balanceRaw);

    var valid = true;
    if (!name) {
        nameInput.classList.add('is-invalid');
        nameError.textContent = 'Please enter an account name.';
        valid = false;
    }
    if (balanceRaw !== '' && Number.isNaN(balance)) {
        balanceInput.classList.add('is-invalid');
        balanceError.textContent = 'Please enter a valid balance.';
        valid = false;
    }
    if (!valid) return;

    Nkap.ui.setBusy('btnUpdateAccount', 'btnUpdateAccountSpinner', 'btnUpdateAccountIcon', true);

    var id = document.getElementById('editAccountId').value;
    Nkap.http.postJson('/accounts/' + id, 'PUT', {
        name: name, accountType: type, balance: balance, financialInstitutionId: financialInstitutionId
    })
    .then(function () {
        $('#editAccountModal').modal('hide');
        globalThis.location.reload();
    })
    .catch(function (err) {
        if (err && typeof err === 'object' && !err.message) {
            Nkap.forms.applyFieldErrors(err, {
                name:    { inputId: 'editAccountNameInput', errorId: 'editAccountNameError' },
                balance: { inputId: 'editAccountBalanceInput', errorId: 'editAccountBalanceError' }
            });
        } else {
            nameInput.classList.add('is-invalid');
            nameError.textContent = 'Something went wrong. Please try again.';
        }
        console.error('Error updating account:', err);
    })
    .finally(function () {
        Nkap.ui.setBusy('btnUpdateAccount', 'btnUpdateAccountSpinner', 'btnUpdateAccountIcon', false);
    });
}

function openAddAccountModal() {
    document.getElementById('accountNameInput').value = '';
    document.getElementById('accountTypeSelect').value = 'CHECKING';
    document.getElementById('accountFinancialInstitutionSelect').value = '';
    document.getElementById('accountBalanceInput').value = '';

    Nkap.forms.clearValidation(
        ['accountNameInput', 'accountTypeSelect', 'accountFinancialInstitutionSelect', 'accountBalanceInput'],
        ['accountNameError', 'accountTypeError', 'accountFinancialInstitutionError', 'accountBalanceError']
    );
    $('#addAccountModal').modal('show');
}

function saveAccount() {
    var nameInput    = document.getElementById('accountNameInput');
    var typeSelect   = document.getElementById('accountTypeSelect');
    var fiSelect     = document.getElementById('accountFinancialInstitutionSelect');
    var balanceInput = document.getElementById('accountBalanceInput');
    var nameError    = document.getElementById('accountNameError');
    var balanceError = document.getElementById('accountBalanceError');

    Nkap.forms.clearValidation(['accountNameInput', 'accountBalanceInput'], ['accountNameError', 'accountBalanceError']);

    var name                   = nameInput.value.trim();
    var type                   = typeSelect.value;
    var financialInstitutionId = fiSelect.value ? Number.parseInt(fiSelect.value, 10) : null;
    var balanceRaw             = balanceInput.value.trim();
    var balance                = balanceRaw === '' ? 0 : Number.parseFloat(balanceRaw);

    var valid = true;
    if (!name) {
        nameInput.classList.add('is-invalid');
        nameError.textContent = 'Please enter an account name.';
        valid = false;
    }
    if (balanceRaw !== '' && Number.isNaN(balance)) {
        balanceInput.classList.add('is-invalid');
        balanceError.textContent = 'Please enter a valid balance.';
        valid = false;
    }
    if (!valid) return;

    Nkap.ui.setBusy('btnSaveAccount', 'btnSaveAccountSpinner', 'btnSaveAccountIcon', true);

    Nkap.http.postJson('/accounts', 'POST', {
        name: name, accountType: type, balance: balance, financialInstitutionId: financialInstitutionId
    })
    .then(function () {
        $('#addAccountModal').modal('hide');
        globalThis.location.reload();
    })
    .catch(function (err) {
        if (err && typeof err === 'object' && !err.message) {
            Nkap.forms.applyFieldErrors(err, {
                name:    { inputId: 'accountNameInput', errorId: 'accountNameError' },
                balance: { inputId: 'accountBalanceInput', errorId: 'accountBalanceError' }
            });
        } else {
            nameInput.classList.add('is-invalid');
            nameError.textContent = 'Something went wrong. Please try again.';
        }
        console.error('Error saving account:', err);
    })
    .finally(function () {
        Nkap.ui.setBusy('btnSaveAccount', 'btnSaveAccountSpinner', 'btnSaveAccountIcon', false);
    });
}

function deleteAccount(btn) {
    if (!confirm('Delete this account? This cannot be undone.')) return;

    var id = btn.getAttribute('data-id');
    btn.disabled = true;

    Nkap.http.del('/accounts/' + id)
        .then(function () {
            globalThis.location.reload();
        })
        .catch(function (err) {
            console.error('Error deleting account:', err);
            alert('Could not delete this account. It may have transactions linked to it or a non-zero balance.');
            btn.disabled = false;
        });
}
