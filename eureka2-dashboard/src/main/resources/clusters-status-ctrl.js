wsCtrlBase.init({
    onErrorHandler: function () {
        console.log("Error received");
    },
    onMsgHandler  : function (msg) {
        var eurekaStatusList = JSON.parse(msg.data);
        $(window).trigger('RenderClusterStatusChart', {clustersInfo : eurekaStatusList });
    },
    fnGetCmd      : function () {
        return "get status";
    }
});


var clustersStatusCtrl = {
    load: function () {
        wsCtrlBase.load();
    }
};
