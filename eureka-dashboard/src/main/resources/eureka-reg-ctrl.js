var eurekaRegistryCtrl = (function () {
    function load() {
        $.get( "/getconfig", function( data ) {
            console.log("Going to websocket connect to " + data.wsport);
            connect(data.wsport);
        }).fail(function() {
            console.log("Error getting ws port");
        });
    }

    function connect(port) {
        var ws = new WebSocket('ws://' + document.location.hostname + ':' + port);
        ws.onopen = function () {
            ws.send("get registry");
        };
        ws.onmessage = function (data, flags) {
            var instNotification = JSON.parse(data.data);
            $(window).trigger('InstanceNotificationReceived', {instInfo : instNotification.data, type : instNotification.kind});
        };
    }

    return {
        load : load
    }

})();
