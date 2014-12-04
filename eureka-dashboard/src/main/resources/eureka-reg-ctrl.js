var eurekaRegistryCtrl = (function () {
    var MaxReconnection = 3;
    var reconnectAttempt = 0;
    var GetRegistryCmd = "get registry";
    var isPageUnloading = false;

    function load() {
        $.get("/getconfig", function (data) {
            console.log("Going to websocket connect to " + data.wsport);
            connect(data.wsport);
        }).fail(function () {
            console.log("Error getting ws port");
        });

        window.onbeforeunload = function () {
            isPageUnloading = true;
        };
    }

    function connect(port) {
        var ws = new WebSocket('ws://' + document.location.hostname + ':' + port);
        ws.onopen = function () {
            ws.send(GetRegistryCmd);
        };
        ws.onmessage = function (data, flags) {
            var currentRegistry = JSON.parse(data.data);
            if (currentRegistry.length > 0) {
                $(window).trigger('RegistryDataReceived', {data: currentRegistry });
            }
        };

        ws.onerror = function (error) {
            console.log("Error detected in eureka stream " + error);
        };

        ws.onclose = function () {
            console.log("Connection closed. Reconnecting #" + reconnectAttempt);
            if (! isPageUnloading && reconnectAttempt < MaxReconnection) {
                reconnectAttempt++;
                connect(port);
            }
        }
    }

    return {
        load: load
    }

})();
