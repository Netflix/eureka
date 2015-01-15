var wsCtrlBase = (function () {
    var MaxReconnection = 3;
    var reconnectAttempt = 0;
    var isPageUnloading = false;
    var ws;
    var onMsgHandler, onErrorHandler, fnGetCmd; // configured with opts

    function load() {
        $.get("/getconfig", function (data) {
            connect(data.wsport);
        }).fail(function () {
            console.log("Error getting ws port");
        });

        window.onbeforeunload = function () {
            isPageUnloading = true;
        };
    }

    function connect(port) {
        console.log("Connecting -- " + port);
        ws = new WebSocket('ws://' + document.location.hostname + ':' + port);

        ws.onopen = function () {
            if (fnGetCmd) {
                ws.send(fnGetCmd());
            }
        };

        ws.onmessage = function (data, flags) {
            if (onMsgHandler) {
                onMsgHandler(data);
            }
        };

        ws.onerror = function (error) {
            if (onErrorHandler) {
                onErrorHandler(error)
            }
        };

        ws.onclose = function () {
            console.log("Connection closed. Reconnecting #" + reconnectAttempt);
            if (!isPageUnloading && reconnectAttempt < MaxReconnection) {
                reconnectAttempt++;
                connect(port);
            }
        }
    }

    function init(opts) {
        onErrorHandler = opts['onErrorHandler'];
        onMsgHandler = opts['onMsgHandler'];
        fnGetCmd = opts['fnGetCmd'];
    }

    return {
        init: init,
        load: load
    };

})();
