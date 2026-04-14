$(".clickable-row").click(function() {
	window.location = $(this).data("href");
});

$(document).ready(function() {
    google.charts.load('current', {
        packages : [ 'corechart', 'bar' ]
    });
    
    google.charts.setOnLoadCallback(drawLineChart);
    
    $('#myTable').DataTable({ 
		order: [[0, "desc"]] 
	});
});

function drawLineChart() {
    var data = new google.visualization.DataTable();
    data.addColumn('string', 'Month');
    data.addColumn('number', 'Balance');
    Object.keys(real_data).forEach(function(key) {
        data.addRow([ key, real_data[key] ]);
    });
    var options = {
        title : 'Balances for last 12 months',
        fontName: 'Bree Serif, cursive',
        legend: {position: 'none'},
        height: 350,
        vAxis: { format: 'currency' },
        hAxis : {
            slantedText:true,
            slantedTextAngle: 60
        }
    };
    var chart = new google.visualization.LineChart(document.getElementById('line_chart_div'));
    chart.draw(data, options);
}

$(function () {
	$("button[id*='create-budget']").click(function () {
		var year = $('#year').val();
		var month = $('#month').val();
		
		$.ajax({
	        type: "POST",
	        contentType: "application/json",
	        url: "/budget/create",
	        data: JSON.stringify({"month": month, "year": year}),
	        dataType: 'json',
	        cache: false,
	        timeout: 600000,
	        success: function (data) {
				document.getElementById("add-budget-error").style.display = "none";
			    window.location.href = '/budget/view/' + data.id;		
	        },
	        error: function (e) {
				document.getElementById("add-budget-error").style.display = "block";		
	        }
	    });
	});
	
	$("button[id*='delete_budget_btn']").click(function () {
		var budgetId = $(this).prop('id').split('_')[3];
		window.location.href = '/budget/delete/' + budgetId;
	});
});

$(function () {
	$('#create-group-btn').click(function (e) {
        e.preventDefault();
        var create_group_url = '/budget/' + budget_id + '/group/create';
        
        $('input').next('div').remove();
	    
        $.post({
            url: create_group_url,
            data: $('#createGroupForm').serialize(),
            success: function (res) {
                if (res.validated) {
					location.reload();
                } else {
                    $.each(res.errorMessages, function (key, value) {
                        $('input[name=' + key + ']').after('<div class="alert alert-warning text-center mt-1 mb-0">' + value + '</div>');
                    });
                }
            }
        })
    });
    
    $("button[id*='edit-group-btn']").click(function (e) {
        e.preventDefault(); 
        var group_id = $(this).prop('id').split('-')[3];
        var edit_group_url = '/budget/' + budget_id + '/group/edit/' + group_id;
        var form_id = '#editGroupForm' + group_id;
        
        $('input').next('div').remove();
	    
        $.post({
            url: edit_group_url,
            data: $(form_id).serialize(),
            success: function (res) {
                if (res.validated) {
					location.reload();
                } else {
                    $.each(res.errorMessages, function (key, value) {
                        $('input[name=' + key + ']').after('<div class="alert alert-warning text-center mt-1 mb-0">' + value + '</div>');
                    });
                }
            }
        })
    });
    
    $("button[id*='delete-group-btn']").click(function (e) {
        e.preventDefault(); 
        var group_id = $(this).prop('id').split('-')[3];
        var delete_group_url = '/budget/' + budget_id + '/group/delete/' + group_id;
	    
        $.post({
            url: delete_group_url,
            data: group_id,
            success: function (res) {
				location.reload();
            }
        })
    });
});

$(function () {
	$("button[id*='create-category-btn']").click(function (e) {
        e.preventDefault();
        var group_id = $(this).prop('id').split('-')[3];
        var create_category_url = '/budget/' + budget_id + '/group/' + group_id + '/category/create';
        
        $('input').next('div').remove();
        form_id = '#createCategoryForm' + group_id;
	    
        $.post({
            url: create_category_url,
            data: $(form_id).serialize(),
            success: function (res) {
                if (res.validated) {
					location.reload();
                } else {
                    $.each(res.errorMessages, function (key, value) {
                        $('input[name=' + key + ']').after('<div class="alert alert-warning text-center mt-1 mb-0">' + value + '</div>');
                    });
                }
            }
        })
    });
    
    $("button[id*='edit-category-btn']").click(function (e) {
        e.preventDefault();
        var splitted_id = $(this).prop('id').split('-');
        var group_id = splitted_id[3];
        var category_id = splitted_id[4];
        var edit_category_url = '/budget/' + budget_id + '/group/' + group_id + '/category/edit/' + category_id;
        var form_id = '#editCategoryForm' + category_id;
        
        $('input').next('div').remove();
	    
        $.post({
            url: edit_category_url,
            data: $(form_id).serialize(),
            success: function (res) {
                if (res.validated) {
					location.reload();
                } else {
                    $.each(res.errorMessages, function (key, value) {
                        $('input[name=' + key + ']').after('<div class="alert alert-warning text-center mt-1 mb-0">' + value + '</div>');
                    });
                }
            }
        })
    });
    
    $("button[id*='delete-category-btn']").click(function (e) {
        e.preventDefault(); 
        var group_id = $(this).prop('id').split('-')[3];
        var category_id = $(this).prop('id').split('-')[4];
        var delete_category_url = '/budget/' + budget_id + '/group/' + group_id + '/category/delete/' + category_id;
        
        $.post({
            url: delete_category_url,
            data: category_id,
            success: function (res) {
				location.reload();
            }
        })
    });
});


$(function () {
	$("button[id*='create-transaction-btn']").click(function (e) {
        e.preventDefault();
        var group_id = $(this).prop('id').split('-')[3];
        var category_id = $(this).prop('id').split('-')[4];
        var create_transaction_url = '/budget/' + budget_id + '/group/' + group_id + '/category/' + category_id + '/transaction/create';
        
        $('input').next('div').remove();
        form_id = '#createTransactionForm' + category_id;
	    
        $.post({
            url: create_transaction_url,
            data: $(form_id).serialize(),
            success: function (res) {
                if (res.validated) {
					location.reload();
                } else {
                    $.each(res.errorMessages, function (key, value) {
                        $('input[name=' + key + ']').after('<div class="alert alert-warning text-center mt-1 mb-0">' + value + '</div>');
                    });
                }
            }
        })
    });
    
    $("button[id*='edit-transaction-btn']").click(function (e) {
        e.preventDefault();
        var splitted_id = $(this).prop('id').split('-');
        var group_id = splitted_id[3];
        var category_id = splitted_id[4];
        var transaction_id = splitted_id[5];
        var edit_transaction_url = '/budget/' + budget_id + '/group/' + group_id + '/category/' + category_id + '/transaction/edit/' + transaction_id;
        var form_id = '#editTransactionForm' + transaction_id;
        
        $('input').next('div').remove();
	    
        $.post({
            url: edit_transaction_url,
            data: $(form_id).serialize(),
            success: function (res) {
                if (res.validated) {
					location.reload();
                } else {
                    $.each(res.errorMessages, function (key, value) {
                        $('input[name=' + key + ']').after('<div class="alert alert-warning text-center mt-1 mb-0">' + value + '</div>');
                    });
                }
            }
        })
    });
    
    $("button[id*='delete-transaction-btn']").click(function (e) {
        e.preventDefault(); 
        var group_id = $(this).prop('id').split('-')[3];
        var category_id = $(this).prop('id').split('-')[4];
        var transaction_id = $(this).prop('id').split('-')[5];
        var delete_transaction_url = '/budget/' + budget_id + '/group/' + group_id + '/category/' + category_id + '/transaction/delete/' + transaction_id;
        
        $.post({
            url: delete_transaction_url,
            data: transaction_id,
            success: function (res) {
				location.reload();
            }
        })
    });
});