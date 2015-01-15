function ClusterStatusChart(options) {
    options = options || {};
    var writeClustersInfo = [],
            readClustersInfo = [],
            containerId = options.containerId || 'cluster',
            width = options.width || 760,
            height = 700,
            radius = Math.min(width, height) / 2;

    var color = d3.scale.ordinal()
            .domain(["G", "Y", "R"])
            .range(["#c7e9c0", "#ec7014", "#fff7bc"]);

    var arc = d3.svg.arc()
            .outerRadius(radius - 10)
            .innerRadius(radius - 100);

    var arcInner = d3.svg.arc()
            .outerRadius(radius / 2 - 10)
            .innerRadius(radius / 2 - 70);

    var pie = d3.layout.pie()
            .padAngle(.01)
            .value(function (d) {
                return d.value;
            });


    function loadData(serversInfo) {
        writeClustersInfo = serversInfo.writeClusters;
        readClustersInfo = serversInfo.readClusters;
        injectClusterType(writeClustersInfo, 'W');
        injectClusterType(readClustersInfo, 'R');
        return this;
    }

    function render() {
        clear();
        var svg = d3.select('#' + containerId).append("svg")
                .attr("width", width)
                .attr("height", height)
                .append("g")
                .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");

        // write clusters
        drawClusterChart(writeClustersInfo, arc, 'arc-outer');

        // read clusters
        drawClusterChart(readClustersInfo, arcInner, 'arc-inner');

        function drawClusterChart(dataForPieChart, arcLayout, arcClassName) {
            var g = svg.selectAll("." + arcClassName)
                    .data(pie(dataForPieChart), function (d) {
                        return d.data.id + '_' + d.data.status;
                    })
                    .enter().append("g")
                    .attr("class", arcClassName);
            drawArc(g, arcLayout);
            drawText(g, arcLayout);
        }

        function drawArc(g, arc) {
            g.append("path")
                    .attr("d", arc)
                    .style("fill", function (d) {
                        return color(d.data.status);
                    })
                    .on('mouseover', function (d) {
                        d3.select(this).style('cursor', 'pointer');
                    })
                    .on('click', onClick);
        }

        function drawText(g, arc) {
            g.append("text")
                    .attr("transform", function (d) {
                        return "translate(" + arc.centroid(d) + ")";
                    })
                    .attr("dy", ".35em")
                    .style("text-anchor", "middle")
                    .text(function (d) {
                        return d.data['type'];
                    });
        }

        function onClick(d) {
            var serverDetails = new ServerDetails(d);
            $('#server-info').html(serverDetails.buildMarkup()).show();
        }

        function clear() {
            $('#' + containerId + ' svg').remove();
        }

    }

    function injectClusterType(clusterInfo, clusterType) {
        for (var i = 0; i < clusterInfo.length; i++) {
            clusterInfo[i].value = 100; // stub value needed for layout algorithm
            clusterInfo[i].type = clusterType;
        }
    }

    return {
        render  : render,
        loadData: loadData
    }
}

function ServerDetails(d) {
    this.serverInfo = d.data || {};
}

ServerDetails.prototype.status = function () {
    return this.serverInfo['status'];
};

ServerDetails.prototype.vipAddress = function () {
    return this.serverInfo['vipAddress'];
};

ServerDetails.prototype.instanceId = function () {
    if ('instanceId' in this.serverInfo['dataCenterInfo']) {
        return this.serverInfo['dataCenterInfo']['instanceId'];
    }
    return '--';
};

ServerDetails.prototype.hostname = function () {
    if ('publicAddress' in this.serverInfo['dataCenterInfo'] &&
            'hostName' in this.serverInfo['dataCenterInfo']['publicAddress']) {
        return this.serverInfo['dataCenterInfo']['publicAddress']['hostName'];
    }

    return '--';
};

ServerDetails.prototype.ports = function () {
    var portsList = "";
    this.serverInfo['ports'].forEach(function (portObj, i) {
        if (portObj.port) {
            if (i != 0) {
                portsList = portsList + "," + portObj.port;
            } else {
                portsList = portObj.port;
            }
        }
    });
    return portsList;
};

ServerDetails.prototype.buildEurekaDashboardLinkForHost = function () {
    return "http://" + this.hostname() + ":8077/admin#view=eureka2&";
};

ServerDetails.prototype.buildMarkup = function () {
    var markup = ['<ul><h5>Eureka Server</h5>'];
    markup.push('<li><span class="name">Status</span> : <span class="value">' + this.status() + '</span></li>');
    markup.push('<li><span class="name">VIP</span> : <span class="value">' + this.vipAddress() + '</span></li>');
    markup.push('<li><span class="name">InstanceId</span> : <span class="value">' + this.instanceId() + '</span></li>');
    markup.push('<li><span class="name">Host</span> : <span class="value">' + this.hostname() + '</span></li>');
    markup.push('<li><span class="name">Ports</span> : <span class="value">' + this.ports() + '</span></li>');
    if (this.hostname() != '--') {
        markup.push('<li><a href="' + this.buildEurekaDashboardLinkForHost() + '" target="_blank">host-dashboard</a>');
    }
    markup.push('</ul>');
    return markup.join('');
};


$(function () {

    var eurekaClustersChart = new ClusterStatusChart({containerId: "eureka-clusters"});
    $(window).on('RenderClusterStatusChart', function (e, data) {
        eurekaClustersChart.loadData({
            writeClusters: filterEurekaServers(data.clustersInfo, ['eureka2-write', 'eureka2_write']),
            readClusters : filterEurekaServers(data.clustersInfo, ['eureka2-read', 'eureka2_read'])
        }).render();
        //readClusterChart.loadData(filterEurekaServers(data.clustersInfo, ['eureka2-read', 'eureka2_read'])).render();

    });


    function filterEurekaServers(clustersInfo, appNamePrefixList) {
        var eurekaSvrList = [], appName;
        clustersInfo.forEach(function (eurekaSvr) {
            appName = eurekaSvr.app.toLowerCase();
            appNamePrefixList.forEach(function (appNamePrefix) {
                if (appName.indexOf(appNamePrefix) > -1) {
                    eurekaSvrList.push(eurekaSvr);
                }
            });
        });
        return eurekaSvrList;
    }

});
