$(document).ready(function () {
    var statusErrorElm = $('#status-error');
    statusErrorElm.html("");

    var oTable = $('#discovery-table').dataTable({
        "aoColumns"     : [
            {"sTitle": "Application", "mDataProp": "appId", sDefaultContent : '-'},
            {"sTitle": "Instance Id", "mDataProp": "instId", sDefaultContent: '-'},
            {"sTitle": "Status", "mDataProp": "status", sDefaultContent: '-'},
            {"sTitle": "IP Address", "mDataProp": "ip", sDefaultContent: '-'},
            {"sTitle": "Hostname", "mDataProp": "hostname", sDefaultContent: '-'}
        ],
        "sAjaxSource"   : "/webadmin/eureka2",
        "fnServerData"  : function (sSource, aoData, fnCallback) {
            $.getJSON(sSource)
                    .success(function (data) {
                        if (data) {
                            statusErrorElm.html("");
                            $("#status-lastupdate").html(new Date().format());
                            $("#status-error").removeClass("status-error");
                            fnCallback({"aaData": data});
                        }
                    })
                    .error(function (jqXHR, textStatus, errorThrown) {
                        statusErrorElm.html(textStatus + ": " + errorThrown);
                        statusErrorElm.addClass("status-error");
                    });
        },

        'bSort'          : true,
        'bLengthChange'  : true,
        'sPaginationType': 'bootstrap',
        'bDestroy'       : true,
        'bFilter'        : true,
        'bStateSave'     : false,
        "iDisplayLength" : 25,
        "fnInfoCallback": function (oSettings, iStart, iEnd, iMax, iTotal, sPre) {
            $("#status-visible").html(iTotal);
            $("#status-total").html(iMax);
            return "";
        },
        'sDom'            : "H<'row'<'span3'l><f>r>t<'row'<'span6'i><p>>" // needed to show header buttons
    });


    // table refresh, filter events
    var bseFilterElm = $('.bse-filter');
    bseFilterElm.val("");
    bseFilterElm.die("keyup").live("keyup", function () {
        $('#discovery-table').dataTable().fnFilter($(".bse-filter").val(), null, false, true);
    });
    bseFilterElm.die("click").live("click", function () {
        $('#discovery-table').dataTable().fnReloadAjax();
    });

});
