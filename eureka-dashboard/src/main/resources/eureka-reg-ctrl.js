var eurekaRegistryCtrl = (function () {
    var registry = {};

    function load() {
        var ws = new WebSocket('ws://localhost:9000');
        ws.onopen = function () {
            ws.send("get apps");
        };
        ws.onmessage = function (data, flags) {
            var instNotification = JSON.parse(data.data);
            if (instNotification.kind === 'Add') {
                console.log("Add - " + instNotification.data['id']);
                addInstance(instNotification.data);
            } else if (instNotification.kind === 'Remove') {
                console.log("Remove - " + instNotification.data['id']);
                removeInstance(instNotification.data);
            }
        };
    }
    
    function addInstance(instInfo) {
        if (instInfo['app'] in registry) {
            registry[instInfo['app']].push(instInfo);
        } else {
            registry[instInfo['app']] = [instInfo];
        }
    }
    
    function removeInstance(instInfo) {
        if (instInfo['app'] in registry) {
            registry[instInfo['app']] = registry[instInfo['app']].filter(function (elm) {
                return elm['id'] !== instInfo['id'];
            });
        }
    }

    function getRegistry() {
        return registry;
    }

    return {
        load : load,
        getRegistry : getRegistry
    }
})();
