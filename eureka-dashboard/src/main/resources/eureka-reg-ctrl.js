var eurekaRegistryCtrl = (function () {
    function load() {
        var ws = new WebSocket('ws://localhost:9000');
        ws.onopen = function () {
            ws.send("get apps");
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
