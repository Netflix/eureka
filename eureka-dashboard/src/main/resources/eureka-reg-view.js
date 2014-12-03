var eurekaRegistryView = (function () {
    var containerId;
    var diameter;
    var registry = [];
    var totalInstCount = 0;
    var sortedAppList = [];
    var instancesTblView, // ref to SimpleTreeView for instance list
            selectAppAutoCompleteBox;
    var firstViewLoaded = false;
    var svg, bubble, format, color; // visualization
    var REFRESH_VIEW_INTERVAL = 1000;
    var CLEAN_UP_LIVE_STREAM_INTERVAL = 5000;

    function init(options) {
        options = options || {};
        containerId = options.containerId || 'registry';
        diameter = options.diameter || 800;

        $(window).on('InstanceNotificationsReceived', function (e, data) {
            console.log("Updates received " + data.notifications.length);
            data.notifications.forEach(function (notification) {
                updateReceived(notification);
            });
        });


        $(window).on('ResetView', function (e) {
            resetReceived();
        });

        window.setInterval(function () {
            refreshView();
        }, REFRESH_VIEW_INTERVAL);

        window.setInterval(function () {
            cleanUpLiveStream();
        }, CLEAN_UP_LIVE_STREAM_INTERVAL);
    }

    var updatedInstInfo = [];
    var lastUpdatedInfoIndex = 0;
    function updateReceived(notification) {
        var instInfo = notification.data;
        updatedInstInfo.push(instInfo);

        if (notification.kind === 'Add') {
            if (instInfo['app'] in registry) {
                registry[instInfo['app']].push(instInfo);
            } else {
                registry[instInfo['app']] = [instInfo];
            }
        } else {
            // remove
            if (instInfo['app'] in registry) {
                registry[instInfo['app']] = registry[instInfo['app']].filter(function (elm) {
                    return elm['id'] !== instInfo['id'];
                });
            }
        }

        if (firstViewLoaded) {
            updateLiveStream(notification);
        }
    }

    function updateLiveStream(notification) {
        $('.live-stream').show().prepend($('<li>' + buildLiveStreamEntry(notification) + '</li>'));
    }

    function resetReceived() {
        registry = [];
        $('.live-stream').show().prepend($('<li>Reconnecting ...</li>'));
        render();
    }

    function buildLiveStreamEntry(notification) {
        var vipAddress = notification['data']['vipAddress'] || '';
        var markup = [];

        markup.push('<span class="entry-type">' + notification.kind + '</span>');
        markup.push(' => ');
        markup.push('<span class="app-name">' + notification['data']['app'] + '</span>, ' );
        if (vipAddress) {
            markup.push(vipAddress + ", ");
        }
        markup.push(notification['data']['dataCenterInfo']['instanceId']);
        return markup.join('');
    }

    function refreshView() {
        if (lastUpdatedInfoIndex == updatedInstInfo.length)  {
            // no update in refresh interval
            console.log("REfreshing updates len = " + updatedInstInfo.length);
            if (updatedInstInfo.length > 0) {
                hideMsg();
                render();
            }
            updatedInstInfo = [];
            lastUpdatedInfoIndex = 0;
        } else {
            lastUpdatedInfoIndex = updatedInstInfo.length;
        }
        if (!firstViewLoaded) {
            firstViewLoaded = true;
        }
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
            var nacLink = Utils.buildNACLinkForInstance(inst['dataCenterInfo']['instanceId']);
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

    var MAX_LIVE_STREAM_ROWS = 5000;
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
        init  : init
    }
})

();
