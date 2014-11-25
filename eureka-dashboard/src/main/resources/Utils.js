var Utils = (function() {
    var curEnv = {env : 'test', region : 'us-east-1'};

    return {
        buildNACLinkForInstance : function(instId) {
            var link = 'http://asgard' + curEnv['env'] + '.netflix.com/' + curEnv['region'] + '/instance/show/' + instId;
            return '<a target="_blank" href="' + link + '">' + instId + '</a>';
        }
    }

})();