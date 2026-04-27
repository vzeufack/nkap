/* budget.js — nkap budgeting page */

$(document).ready(function () {

    // ── Month-only datepicker ──
    const $picker = $('#monthPicker');

    $picker.datepicker({
        format: "MM yyyy",
        startView: "months",
        minViewMode: "months",
        autoclose: true,
        todayHighlight: true
    });

    // Set to current month on load if empty
    if (!$picker.val()) {
        $picker.datepicker('setDate', new Date());
    }

    // Helper: get current picker date
    function getPickerDate() {
        return $picker.datepicker('getDate') || new Date();
    }

    // Helper: set picker date and optionally reload page
    function setPickerDate(date) {
        $picker.datepicker('setDate', date);
        // Uncomment to reload page with selected month:
        // const yyyy = date.getFullYear();
        // const mm   = String(date.getMonth() + 1).padStart(2, '0');
        // window.location.href = '/budget?month=' + yyyy + '-' + mm;
    }

    // ── Previous month ──
    $('#btnPrevMonth').on('click', function () {
        const d = getPickerDate();
        d.setDate(1);
        d.setMonth(d.getMonth() - 1);
        setPickerDate(d);
    });

    // ── Next month ──
    $('#btnNextMonth').on('click', function () {
        const d = getPickerDate();
        d.setDate(1);
        d.setMonth(d.getMonth() + 1);
        setPickerDate(d);
    });

    // ── Today (current month) ──
    $('#btnToday').on('click', function () {
        setPickerDate(new Date());
    });

    // ── React to manual datepicker selection ──
    $picker.on('changeDate', function (e) {
        const yyyy = e.date.getFullYear();
        const mm   = String(e.date.getMonth() + 1).padStart(2, '0');
        console.log('Period selected:', yyyy + '-' + mm);
        // window.location.href = '/budget?month=' + yyyy + '-' + mm;
    });
});