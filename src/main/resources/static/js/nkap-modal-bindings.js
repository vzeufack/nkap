/* nkap-modal-bindings.js — shared modal focus bindings + Enter-to-submit shortcut.
 *
 * Wrapped in $(document).ready(...) because this file is loaded (via <script src>) before
 * the modal markup appears further down home.html — the bindings must attach only once the
 * full DOM (including those modals) has been parsed.
 */
$(document).ready(function () {

    $('#addTransactionModal').on('shown.bs.modal', function() {
        document.getElementById('txAmountInput').focus();
    });

    $('#addGroupModal').on('shown.bs.modal', function() {
        document.getElementById('groupNameInput').focus();
    });

    $('#addCategoryModal').on('shown.bs.modal', function() {
        document.getElementById('categoryNameInput').focus();
    });

    $('#addAccountModal').on('shown.bs.modal', function() {
        document.getElementById('accountNameInput').focus();
    });

    $('#editAccountModal').on('shown.bs.modal', function() {
        document.getElementById('editAccountNameInput').focus();
    });

    $('#editTransactionModal').on('shown.bs.modal', function() {
        document.getElementById('editTxAmountInput').focus();
    });

    $('#transferBalanceModal').on('shown.bs.modal', function() {
        document.getElementById('transferAmountInput').focus();
    });

    document.addEventListener('keydown', function(e) {
        if (e.key !== 'Enter') return;
        var active = document.activeElement;
        if (!active) return;
        if (active.id === 'txAmountInput' ||
            active.id === 'txDateInput'   ||
            active.id === 'txNoteInput')              saveTransaction();
        if (active.id === 'editTxAmountInput' ||
            active.id === 'editTxDateInput'   ||
            active.id === 'editTxNoteInput')          updateTransaction();
        if (active.id === 'groupNameInput')         saveGroup();
        if (active.id === 'categoryNameInput'    ||
            active.id === 'categoryAllocationInput' ||
            active.id === 'categoryBalanceInput')     saveCategory();
        if (active.id === 'accountNameInput'     ||
            active.id === 'accountBalanceInput')      saveAccount();
        if (active.id === 'editAccountNameInput' ||
            active.id === 'editAccountBalanceInput')  updateAccount();
        if (active.id === 'transferAmountInput')      saveTransfer();
    });

});
