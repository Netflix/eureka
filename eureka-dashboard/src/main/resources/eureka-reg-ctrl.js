var eurekaRegistryCtrl = (function () {
    var instances = {};

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
            var sendNotification = false;
            if (instNotification.kind === 'Add') {
                if (instNotification.data['id'] in instances) {
                    console.log("Duplicate ? " + instNotification.data['id']);
                } else {
                    instances[instNotification.data['id']] = 1;
                    sendNotification = true;
                }
            } else {
                // remove instance
                if (instNotification.data['id'] in instances) {
                    delete instances[instNotification.data['id']];
                    sendNotification = true;
                } else {
                    console.log("Removing instance that don't exist in registry ? " + instNotification.data['id']);
                }
            }

            if (sendNotification) {
                $(window).trigger('InstanceNotificationReceived', {instInfo : instNotification.data, type : instNotification.kind});
            }
        };
    }

    return {
        load : load
    }

})();
