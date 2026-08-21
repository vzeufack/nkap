/* nkap-core.js — shared helpers used across the budgeting page's feature scripts.
 *
 * Exposes a single global `Nkap` namespace:
 *   - Nkap.http  — fetch wrappers (JSON, multipart, DELETE) that normalize error handling
 *   - Nkap.forms — validation-state helpers (clear/apply is-invalid + error text)
 *   - Nkap.ui    — small DOM/UI helpers (busy-button toggling, budget id lookup,
 *                  category dropdown building, the nkap:groupSaved event)
 *
 * Feature-specific functions (the ones wired up via onclick="..." in the templates)
 * live in the other nkap-*.js files and in fragments/budget-plan.html's own inline
 * script; they call into this namespace instead of duplicating this logic.
 */
(function (global) {
    'use strict';

    var Nkap = global.Nkap = global.Nkap || {};

    Nkap.http = {
        /**
         * POST/PUT JSON. Resolves with the parsed JSON body on success; rejects with the
         * parsed JSON error body (or an Error if the body isn't JSON) on a non-2xx response.
         */
        postJson: function (url, method, body) {
            return fetch(url, {
                method: method,
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(body)
            }).then(function (response) {
                if (response.ok) return response.json();
                return response.json().then(function (errs) { throw errs; });
            });
        },

        /** POST a FormData payload (multipart) — same success/error shape as postJson. */
        postForm: function (url, formData) {
            return fetch(url, { method: 'POST', body: formData })
                .then(function (response) {
                    if (response.ok) return response.json();
                    return response.json().then(function (errs) { throw errs; });
                });
        },

        /** DELETE a resource. Resolves with no value on success; rejects with an Error on failure. */
        del: function (url) {
            return fetch(url, { method: 'DELETE' }).then(function (response) {
                if (!response.ok) throw new Error('Request failed: ' + response.status);
            });
        }
    };

    Nkap.forms = {
        /** Removes .is-invalid from fieldIds and clears the text content of errorIds. */
        clearValidation: function (fieldIds, errorIds) {
            (fieldIds || []).forEach(function (id) {
                var el = document.getElementById(id);
                if (el) el.classList.remove('is-invalid');
            });
            (errorIds || []).forEach(function (id) {
                var el = document.getElementById(id);
                if (el) el.textContent = '';
            });
        },

        /**
         * Maps a JSON field-error response onto {inputId, errorId} pairs.
         * fieldMap: { <jsonKey>: { inputId, errorId, message? } } — message overrides
         * the server-provided text (used where a friendlier message is shown instead).
         */
        applyFieldErrors: function (errs, fieldMap) {
            if (!errs || typeof errs !== 'object') return;
            Object.keys(fieldMap).forEach(function (key) {
                if (errs[key]) {
                    var m = fieldMap[key];
                    var input = document.getElementById(m.inputId);
                    var error = document.getElementById(m.errorId);
                    if (input) input.classList.add('is-invalid');
                    if (error) error.textContent = m.message || errs[key];
                }
            });
        }
    };

    Nkap.ui = {
        /** Toggles the spinner/icon/disabled-button triplet used on every async submit button. */
        setBusy: function (btnId, spinnerId, iconId, busy) {
            var spinner = document.getElementById(spinnerId);
            var icon    = document.getElementById(iconId);
            var btn     = document.getElementById(btnId);
            if (spinner) spinner.classList.toggle('d-none', !busy);
            if (icon)    icon.classList.toggle('d-none', busy);
            if (btn)     btn.disabled = busy;
        },

        /** Notifies the page that a group/category/account/transaction mutation succeeded. */
        dispatchGroupSaved: function () {
            document.dispatchEvent(new CustomEvent('nkap:groupSaved'));
        },

        /** Reads the current budget id off the rendered budget-plan fragment. */
        getBudgetId: function () {
            var el = document.querySelector('[data-budget-id]');
            return el ? el.getAttribute('data-budget-id') : null;
        },

        /**
         * Builds <optgroup>/<option> elements for a category <select> from the rendered
         * budget-plan fragment's group cards.
         *   opts.clearAll      — true: wipe all existing options first (transfer modal).
         *                         false: keep the first (placeholder) option (tx category dropdown).
         *   opts.includeBalance — true: also set opt.dataset.balance from the row's balance
         *                         (needed by the transfer modal's "amount available" logic).
         */
        buildCategoryOptions: function (selectEl, opts) {
            opts = opts || {};
            var currentVal = selectEl.value;

            if (opts.clearAll) {
                selectEl.innerHTML = '';
            } else {
                while (selectEl.children.length > 1) selectEl.lastChild.remove();
            }

            document.querySelectorAll('#budget-plan-container .group-card').forEach(function (card) {
                var nameEl = card.querySelector('.group-card-name');
                var rows   = card.querySelectorAll('.cat-row[data-category-id]');
                if (!nameEl || rows.length === 0) return;

                var optgroup = document.createElement('optgroup');
                optgroup.label = nameEl.textContent.trim();

                rows.forEach(function (row) {
                    var catId   = row.dataset.categoryId;
                    var catName = row.querySelector('.cat-name');
                    if (!catId || !catName) return;

                    var opt = document.createElement('option');
                    opt.value       = catId;
                    opt.textContent = catName.textContent.trim();
                    if (opts.includeBalance) opt.dataset.balance = row.dataset.categoryBalance || '0';
                    optgroup.appendChild(opt);
                });

                selectEl.appendChild(optgroup);
            });

            if (currentVal) selectEl.value = currentVal;
        }
    };

})(window);
