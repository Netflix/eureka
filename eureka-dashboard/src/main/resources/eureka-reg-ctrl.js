var eurekaRegistryCtrl = (function () {
    var MaxReconnection = 3;
    var reconnectAttempt = 0;
    var GetRegistryCmd = "get registry";

    function load() {
        $.get("/getconfig", function (data) {
            console.log("Going to websocket connect to " + data.wsport);
            connect(data.wsport);
        }).fail(function () {
            console.log("Error getting ws port");
        });
    }

    function connect(port) {
        var ws = new WebSocket('ws://' + document.location.hostname + ':' + port);
        ws.onopen = function () {
            ws.send(GetRegistryCmd);
        };
        ws.onmessage = function (data, flags) {
            if (data.data === 'ERROR') {
                ws.send(GetRegistryCmd);
                console.log("Error from eureka-client, resetting view");
                $(window).trigger('ResetView');
            } else {
                var instNotification = JSON.parse(data.data);
                $(window).trigger('InstanceNotificationReceived', {instInfo: instNotification.data, type: instNotification.kind});
            }
        };

        ws.onerror = function (error) {
            console.log("Error detected in eureka stream " + error);
        };

        ws.onclose = function() {
            console.log("Connection closed. Reconnecting #" + reconnectAttempt);
            if (reconnectAttempt < MaxReconnection) {
                reconnectAttempt++;
                connect(port);
            }
        }
    }

    return {
        load: load
    }

})();
