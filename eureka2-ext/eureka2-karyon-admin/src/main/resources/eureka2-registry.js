$(document).ready(function () {
    "use strict"
    var statusErrorElm = $('#status-error');
    statusErrorElm.html("");

  var source = "${ajax_base}/eureka2";

  console.log("Making ajax call - " + source);
    $('#discovery-table-2').dataTable({
        "aoColumns"      : [
            {"sTitle": "Application", "mDataProp": "application", sDefaultContent: '-'},
            {"sTitle": "Instance Id", "mDataProp": "instanceId", sDefaultContent: '-', bSortable: false},
            {"sTitle": "Status", "mDataProp": "status", sDefaultContent: '-'},
            {"sTitle": "IP Address", "mDataProp": "ipAddress", sDefaultContent: '-', bSortable: false},
            {"sTitle": "VIP", "mDataProp": "vipAddress", sDefaultContent: '-'},
            {"sTitle": "Hostname", "mDataProp": "hostname", sDefaultContent: '-'}
        ],
      "sAjaxSource": source,
        "fnServerData"   : function (sSource, aoData, fnCallback) {
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
        "bServerSide"    : true,
        "bProcessing"    : true,
        "sPaginationType": "bootstrap",
        "iDisplayLength" : 100,
        "bLengthChange"  : true,
        "bDestroy"       : true,
        "bFilter"        : true,
        'bStateSave'     : true,
        'sDom'           : "H<'row'<'span3'l><f>r>t<'row'<'span6'i><p>>" // needed to show header buttons
    });


    // table refresh, filter events
    var bseFilterElm = $('.bse-filter');
    bseFilterElm.val("");
    bseFilterElm.die("keyup").live("keyup", function () {
        $('#discovery-table-2').dataTable().fnFilter($(".bse-filter").val(), null, false, true);
    });
    bseFilterElm.die("click").live("click", function () {
        $('#discovery-table-2').dataTable().fnReloadAjax();
    });
});
