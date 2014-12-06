var eurekaRegistryView = (function () {
    var containerId;
    var diameter;
    var registry = [];
    var instances = {};
    var totalInstCount = 0;
    var sortedAppList = [];
    var instancesTblView, // ref to SimpleTreeView for instance list
            selectAppAutoCompleteBox;
    var svg, bubble, format, color; // visualization

    var REFRESH_LIVE_STREAM_INTERVAL = 100;
    var CLEAN_UP_LIVE_STREAM_INTERVAL = 5000;
    var MAX_LIVE_STREAM_ROWS = 5000;
    var logLines = [];

    function init(options) {
        options = options || {};
        containerId = options.containerId || 'registry';
        diameter = options.diameter || 800;

        $(window).on('RegistryDataReceived', function (e, regData) {
            updateRegistry(regData.data);
            hideMsg();
            render();
        });

         window.setInterval(function () {
             streamLogLines();
         }, REFRESH_LIVE_STREAM_INTERVAL);

        window.setInterval(function () {
            cleanUpLiveStream();
        }, CLEAN_UP_LIVE_STREAM_INTERVAL);
    }

    function updateRegistry(instInfoList) {
        var newInstIdList = {};
        instInfoList.forEach(function (instInfo) {
            var instId = instInfo['instanceId'];
            if (!(instId in instances)) {
                addItemToRegistry(instInfo);
                logLines.push({type: 'Add', instId: instId, appId: instInfo['app'], vip: instInfo['vipAddress']});
            } else {
                if (updateRegItemIfNeeded(instInfo)) {
                    logLines.push({type: 'Modify', instId: instId, appId: instInfo['app'], vip: instInfo['vipAddress']});
                }
            }
            newInstIdList[instId] = true;
        });

        // existing keys - find remove ?
        Object.keys(instances).forEach(function (oldInstId) {
            if (!(oldInstId in newInstIdList)) {
                var oldInstInfo = instances[oldInstId];
                removeItemFromRegistry(instances[oldInstId]);
                logLines.push({type: 'Add', instId: oldInstId, appId: oldInstInfo['app'], vip: oldInstInfo['vipAddress']});
            }
        });
    }

    function removeItemFromRegistry(instInfo) {
        if (instInfo['app'] in registry) {
            registry[instInfo['app']] = registry[instInfo['app']].filter(function (elm) {
                return elm['instanceId'] !== instInfo['instanceId'];
            });
        }
        delete instances[instInfo['instanceId']];
    }

    function addItemToRegistry(instInfo) {
        instances[instInfo['instanceId']] = instInfo;
        if (instInfo['app'] in registry) {
            registry[instInfo['app']].push(instInfo);
        } else {
            registry[instInfo['app']] = [instInfo];
        }
    }

    function updateRegItemIfNeeded(instInfo) {
        var instId = instInfo['instanceId'];
        var isUpdated = false;

        // update VIP address
        if ((instId in instances) && (instInfo['vipAddress'] !== instances[instId]['vipAddress'])) {
            instances[instId]['vipAddress'] = instInfo['vipAddress'];
            isUpdated = true;
        }

        // update app - should not usually happen
        if ((instId in instances) && (instInfo['app'] !== instances[instId]['app'])) {
            var oldInstInfo = instances[instId];
            removeItemFromRegistry(oldInstInfo);
            addItemToRegistry(instInfo);
            isUpdated = true;
        }
        return isUpdated;
    }

    function streamLogLines() {
        // stream batch of log lines
        if (logLines.length > 5000) {
            logLines.splice(5000);
        }

        for (var i = 0; i < 500 && logLines.length > 0; i++) {
            var logLine = logLines.pop();
            if (logLine) {
                $('.live-stream').show().prepend($('<li>' + buildLiveStreamEntry(logLine) + '</li>'));
            }
        }
    }

    function buildLiveStreamEntry(logLine) {
        var vipAddress = logLine['vip'] || '';
        var markup = [];

        markup.push('<span class="entry-type">' + logLine['type'] + '</span>');
        markup.push(' => ');
        markup.push('<span class="app-name">' + logLine['appId'] + '</span>, ');
        if (vipAddress) {
            markup.push(vipAddress + ", ");
        }
        markup.push(logLine['instId']);
        return markup.join('');
    }

    function hideMsg() {
        $('.msg').text('');
    }

    function buildAndLoadSortedAppList() {
        var instanceCount;
        totalInstCount = 0;
        sortedAppList = [];
        for (var appId in registry) {
            instanceCount = registry[appId].length;
            sortedAppList.push({name: appId, value: instanceCount});
            totalInstCount += instanceCount;
        }

        sortedAppList.sort(function (a, b) {
            return b['value'] - a['value'];
        });

        return { children: sortedAppList };
    }

    function showTotalCount() {
        var regCntElm = d3.select('.reg-count');
        regCntElm.select('.val').text(totalInstCount);
        regCntElm.style('display', 'block');
    }

    function showInstanceList(app, instances) {
        instancesTblView = SimpleTreeView({contId: 'inst-list', data: buildInstancesMapByStatus(instances)});
        instancesTblView.init();

        $('.inst-list-cont .app').html('&ldquo;' + app + '&rdquo;');
        $('.inst-list-cont').show();
    }


    function buildInstancesMapByStatus(instances) {
        var instStatusMap = {};
        instances.forEach(function (inst) {
            var nacLink = Utils.buildNACLinkForInstance(inst['instanceId']);
            if (inst['status'] in instStatusMap) {
                instStatusMap[inst['status']].push(nacLink);
            } else {
                instStatusMap[inst['status']] = [nacLink];
            }
        });

        return instStatusMap;
    }

    function wireSearchBox() {
        selectAppAutoCompleteBox = $('#search-app').autocomplete({
            source: sortedAppList.map(function (o) {
                return o.name;
            }),
            select: function (event, ui) {
                searchApp(ui.item.value);
            }
        });
    }

    function searchApp(app) {
        var registryForApp = registry[app] || [];
        showInstanceList(app, registryForApp);
    }

    function showSearchBox() {
        $('#search-box-cont').show();
    }

    function render() {
        var data = buildAndLoadSortedAppList();
        renderBubbleChart(data);
        showTotalCount();
        showSearchBox();
        wireSearchBox();
    }

    function renderBubbleChart(data) {
        format = d3.format(",d");
        color = d3.scale.category20c();

        bubble = d3.layout.pack()
                .sort(null)
                .size([diameter, diameter])
                .padding(1.5);
        clear();

        svg = d3.select('#' + containerId).append("svg")
                .attr("width", diameter)
                .attr("height", diameter)
                .attr("class", "bubble");

        updateView(data);
        d3.select(self.frameElement).style("height", diameter + "px");

        function clear() {
            $('#' + containerId).empty();
        }
    }

    function handleOnClickNode(d) {
        $('#search-app').val(d.name.toLowerCase()); // update search box text to keep it in sync
        searchApp(d.name);
    }

    function updateView(data) {
        var nodes = svg.selectAll(".node")
                .data(bubble.nodes(data)
                        .filter(function (d) {
                            return !d.children;
                        }), function (d) {
                    return d.name + "-" + d.value;
                });

        // remove nodes on exit
        nodes.exit().remove();

        var node = nodes.enter().append("g")
                .attr("class", "node")
                .attr("transform", function (d) {
                    return "translate(" + d.x + "," + d.y + ")";
                });

        node.append("title")
                .text(function (d) {
                    return d.name + ": " + format(d.value);
                });

        node.append("circle")
                .attr("r", function (d) {
                    return d.r;
                })
                .style("fill", function (d) {
                    return color(d.name);
                }).on('mouseover', function (d) {
                    d3.select(this).style('cursor', 'pointer');
                }).on('click', function (d) {
                    handleOnClickNode(d);
                });

        var labels = node.append("text")
                .attr("dy", ".3em")
                .style("text-anchor", "middle")
                .text(function (d) {
                    if (d.name) {
                        return d.name.substring(0, d.r / 3);
                    }
                    return "";
                }).on('mouseover', function (d) {
                    d3.select(this).style('cursor', 'pointer');
                }).on('click', function (d) {
                    handleOnClickNode(d);
                });

        labels.append('tspan')
                .attr('y', function (d) {
                    return (d.r / 2.0);
                })
                .attr('x', '0')
                .style("text-anchor", "middle")
                .text(function (d) {
                    if (d.r > 20) {
                        return format(d.value);
                    }
                    return '';
                });

    }

    function cleanUpLiveStream() {
        var liElms = $('.live-stream li');
        var totalRecords = liElms.length;
        for (var i = MAX_LIVE_STREAM_ROWS; i < totalRecords; i++) {
            liElms[i].remove();
        }
        var rowsDeleted = totalRecords - MAX_LIVE_STREAM_ROWS;
        if (rowsDeleted > 0) {
            console.log("Live stream rows cleaned up : " + rowsDeleted);
        }
    }

    return {
        init: init
    }
})

();
