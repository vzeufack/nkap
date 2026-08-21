/* nkap-bulk-upload.js — Bulk Upload modal logic (CSV preview → confirm flow).
 * Loaded persistently (see home.html) so it stays available across htmx fragment swaps.
 * Uses the shared Nkap.http / Nkap.forms / Nkap.ui helpers from nkap-core.js.
 */

var bulkUploadCachedRows = [];

function openBulkUploadModal() {
    document.getElementById('bulkUploadAccountSelect').value = '';
    document.getElementById('bulkUploadFileInput').value = '';
    Nkap.forms.clearValidation(
        ['bulkUploadAccountSelect', 'bulkUploadFileInput'],
        ['bulkUploadAccountError', 'bulkUploadFileError']
    );
    hideBulkUploadErrors();
    bulkUploadCachedRows = [];
    showBulkUploadFormScreen();
    $('#bulkUploadModal').modal('show');
}

function showBulkUploadFormScreen() {
    document.getElementById('bulkUploadFormScreen').classList.remove('d-none');
    document.getElementById('bulkUploadPreviewScreen').classList.add('d-none');
    document.getElementById('btnBulkUploadBack').classList.add('d-none');
    document.getElementById('btnBulkUploadConfirm').classList.add('d-none');
    document.getElementById('btnBulkUploadPreview').classList.remove('d-none');
}

function showBulkUploadPreviewScreen() {
    document.getElementById('bulkUploadFormScreen').classList.add('d-none');
    document.getElementById('bulkUploadPreviewScreen').classList.remove('d-none');
    document.getElementById('btnBulkUploadBack').classList.remove('d-none');
    document.getElementById('btnBulkUploadConfirm').classList.remove('d-none');
    document.getElementById('btnBulkUploadPreview').classList.add('d-none');
}

function backToBulkUploadForm() {
    hideBulkUploadErrors();
    showBulkUploadFormScreen();
}

function hideBulkUploadErrors() {
    var box = document.getElementById('bulkUploadErrorList');
    box.innerHTML = '';
    box.classList.add('d-none');
}

function showBulkUploadErrors(errors) {
    var box = document.getElementById('bulkUploadErrorList');
    box.innerHTML = '';
    var list = document.createElement('ul');
    list.className = 'mb-0';
    (errors && errors.length ? errors : ['Something went wrong. Please try again.']).forEach(function(msg) {
        var li = document.createElement('li');
        li.textContent = msg;
        list.appendChild(li);
    });
    box.appendChild(list);
    box.classList.remove('d-none');
}

function previewBulkUpload() {
    var accountSelect = document.getElementById('bulkUploadAccountSelect');
    var fileInput      = document.getElementById('bulkUploadFileInput');
    var accountError   = document.getElementById('bulkUploadAccountError');
    var fileError      = document.getElementById('bulkUploadFileError');

    Nkap.forms.clearValidation(
        ['bulkUploadAccountSelect', 'bulkUploadFileInput'],
        ['bulkUploadAccountError', 'bulkUploadFileError']
    );
    hideBulkUploadErrors();

    var valid = true;
    if (!accountSelect.value) {
        accountSelect.classList.add('is-invalid');
        accountError.textContent = 'Please select an account.';
        valid = false;
    }
    if (!fileInput.files || fileInput.files.length === 0) {
        fileInput.classList.add('is-invalid');
        fileError.textContent = 'Please select a CSV file.';
        valid = false;
    }
    if (!valid) return;

    Nkap.ui.setBusy('btnBulkUploadPreview', 'btnBulkUploadPreviewSpinner', 'btnBulkUploadPreviewIcon', true);

    var formData = new FormData();
    formData.append('accountId', accountSelect.value);
    formData.append('file', fileInput.files[0]);

    Nkap.http.postForm('/bulk-upload/preview', formData)
        .then(function (data) {
            bulkUploadCachedRows = data.rows || [];
            renderBulkUploadPreview(data);
            showBulkUploadPreviewScreen();
        })
        .catch(function (err) {
            if (err && Array.isArray(err.errors)) {
                showBulkUploadErrors(err.errors);
            } else if (err && err.message) {
                showBulkUploadErrors([err.message]);
            } else {
                showBulkUploadErrors(null);
            }
            console.error('Error previewing bulk upload:', err);
        })
        .finally(function () {
            Nkap.ui.setBusy('btnBulkUploadPreview', 'btnBulkUploadPreviewSpinner', 'btnBulkUploadPreviewIcon', false);
        });
}

function renderBulkUploadPreview(data) {
    var body = document.getElementById('bulkUploadPreviewBody');
    body.innerHTML = '';
    (data.rows || []).forEach(function(row) {
        var tr = document.createElement('tr');

        var dateTd = document.createElement('td');
        dateTd.textContent = row.transactionDate;
        tr.appendChild(dateTd);

        var descTd = document.createElement('td');
        descTd.textContent = row.description;
        tr.appendChild(descTd);

        var dirTd = document.createElement('td');
        dirTd.textContent = row.direction === 'DEBIT' ? 'Debit' : 'Credit';
        tr.appendChild(dirTd);

        var amountTd = document.createElement('td');
        amountTd.className = 'cat-col-number' + (row.direction === 'DEBIT' ? ' amount-debit' : '');
        amountTd.textContent = '$' + Number(row.amount).toFixed(2);
        tr.appendChild(amountTd);

        body.appendChild(tr);
    });

    var note = document.getElementById('bulkUploadBudgetsNote');
    var budgets = data.budgetsToCreate || [];
    if (budgets.length > 0) {
        var monthNames = budgets.map(function(b) {
            return b.month.charAt(0) + b.month.slice(1).toLowerCase() + ' ' + b.year;
        });
        note.textContent = 'This will also create budgets for: ' + monthNames.join(', ') + '.';
        note.classList.remove('d-none');
    } else {
        note.textContent = '';
        note.classList.add('d-none');
    }
}

function confirmBulkUpload() {
    var accountSelect = document.getElementById('bulkUploadAccountSelect');
    Nkap.ui.setBusy('btnBulkUploadConfirm', 'btnBulkUploadConfirmSpinner', 'btnBulkUploadConfirmIcon', true);
    hideBulkUploadErrors();

    var payload = {
        accountId: Number.parseInt(accountSelect.value, 10),
        transactions: bulkUploadCachedRows.map(function(row) {
            return {
                transactionDate: row.transactionDate,
                amount: row.amount,
                direction: row.direction,
                description: row.description
            };
        })
    };

    Nkap.http.postJson('/bulk-upload/confirm', 'POST', payload)
        .then(function () {
            $('#bulkUploadModal').modal('hide');
            Nkap.ui.dispatchGroupSaved();
        })
        .catch(function (err) {
            if (err && Array.isArray(err.errors)) {
                showBulkUploadErrors(err.errors);
            } else if (err && typeof err === 'object' && !err.message) {
                showBulkUploadErrors(Object.values(err));
            } else {
                showBulkUploadErrors(null);
            }
            console.error('Error confirming bulk upload:', err);
        })
        .finally(function () {
            Nkap.ui.setBusy('btnBulkUploadConfirm', 'btnBulkUploadConfirmSpinner', 'btnBulkUploadConfirmIcon', false);
        });
}
