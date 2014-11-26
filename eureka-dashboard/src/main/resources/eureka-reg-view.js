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

    function init(options) {
        options = options || {};
        containerId = options.containerId || 'registry';
        diameter = options.diameter || 800;

        $(window).on('InstanceNotificationReceived', function (e, data) {
            updateReceived(data);
        });

        window.setInterval(function () {
            refreshView();
        }, 1000);

    }

    var updatedInstInfo = [];
    function updateReceived(data) {
        var instInfo = data.instInfo;
        updatedInstInfo.push(instInfo);

        if (data.type === 'Add') {
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
            updateLiveStream(data);
        }
    }

    function updateLiveStream(data) {
        $('.live-stream').show().prepend($('<li>' + buildLiveStreamEntry(data) + '</li>'));
    }

    function buildLiveStreamEntry(data) {
        var vipAddress = data['instInfo']['vipAddress'] || '';
        var markup = [];

        markup.push('<span class="entry-type">' + data.type + '</span>');
        markup.push(' => ');
        markup.push('<span class="app-name">' + data['instInfo']['app'] + '</span>, ' );
        if (vipAddress) {
            markup.push(vipAddress + ", ");
        }
        markup.push(data['instInfo']['dataCenterInfo']['instanceId']);
        return markup.join('');
    }

    function refreshView() {
        console.log("REfreshing");
        if (updatedInstInfo.length > 0) {
            console.log("For Real");
            hideMsg();
            render();
        }
        if (!firstViewLoaded) {
            firstViewLoaded = true;
        }
        updatedInstInfo = [];
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

    return {
        init  : init
    }
})

();
