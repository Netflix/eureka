$(document).ready(function () {
  "use strict"
  var statusErrorElm = $('#status-error');
  statusErrorElm.html("");

  var source = "${ajax_base}/eurekaStatus/aggregated";

  console.log("Making ajax call - " + source);
  $('#eureka-aggregated-status-table').dataTable({
    "aoColumns": [
      {"sTitle": "Title", "mDataProp": "descriptor.title", sDefaultContent: '-', "sWidth": "20%"},
      {"sTitle": "Current status", "mDataProp": "status", sDefaultContent: '-', "sWidth": "10%"},
      {"sTitle": "Status values", "mDataProp": "descriptor.statuses", sDefaultContent: '-', "sWidth": "20%"},
      {"sTitle": "Component", "mDataProp": "descriptor.className", sDefaultContent: '-', "sWidth": "20%"},
      {"sTitle": "Description", "mDataProp": "descriptor.description", sDefaultContent: '-', "sWidth": "30%"}
    ],
    "sAjaxSource": source,
    "fnServerData": function (sSource, aoData, fnCallback) {
      $.getJSON(sSource, aoData, function (json) {
        $("#status-lastupdate").html(new Date().format());
        if (json.iTotalDisplayRecords) {
          $("#status-visible").html(json.iTotalDisplayRecords);
        }
        if (json.iTotalRecords) {
          $("#status-total").html(json.iTotalRecords);
        }
        fnCallback(json);
      });
    },
    "bServerSide": true,
    "bProcessing": true,
    "bPaginate": false,
    "bLengthChange": true,
    "bDestroy": true,
    "bFilter": false,
    'bStateSave': true,
    'sDom': "H<'row'<'span3'l><f>r>t<'row'<'span6'i><p>>" // needed to show header buttons
  });

  // table refresh, filter events
  var bseFilterElm = $('.bse-filter');
  bseFilterElm.val("");
  bseFilterElm.die("keyup").live("keyup", function () {
    $('#eureka-status-table').dataTable().fnFilter($(".bse-filter").val(), null, false, true);
  });
  bseFilterElm.die("click").live("click", function () {
    $('#eureka-status-table').dataTable().fnReloadAjax();
  });
});
