function ClusterStatusChart(options) {
    options = options || {};
    var data = [],
            containerId = options.containerId || 'cluster',
            width = options.width || 560,
            height = 500,
            radius = Math.min(width, height) / 2,
            outerRadius = radius - 10,
            innerRadius = outerRadius / 2;


    var color = d3.scale.ordinal()
            .domain(["G", "Y", "R"])
            .range(["#c7e9c0", "#ec7014", "#fff7bc"]);

    var arc = d3.svg.arc()
            .padRadius(outerRadius)
            .innerRadius(innerRadius);

    var pie = d3.layout.pie()
            .padAngle(.02)
            .value(function (d) {
                return d.value;
            });



    function loadData(serversInfo) {
        data = serversInfo;
        injectStubValue(data);
        return this;
    }

    function render() {

        function clear() {
            $('#' + containerId).empty();
        }

        clear();

        var svg = d3.select('#' + containerId).append("svg")
                .attr("width", width)
                .attr("height", height)
                .append("g")
                .attr("transform", "translate(" + width / 2 + "," + height / 2 + ")");

        var nodeStatus = svg.append("g").append("text")
                .attr("class", "node-status")
                .attr("y", "-2em")
                .attr("x", "-8em")
                .attr('opacity', '0')
                .attr('text-anchor', 'left')
                .text("DOWN");

        var nodeVip = svg.append("g").append("text")
                .attr("class", "node-vip")
                .attr("y", "-1em")
                .attr("x", "-8em")
                .attr('opacity', '0')
                .attr('text-anchor', 'left')
                .text("VIP");

        var nodeId = svg.append("g").append("text")
                .attr("class", "node-id")
                .attr("y", "0em")
                .attr("x", "-8em")
                .attr('opacity', '0')
                .attr('text-anchor', 'left')
                .text("InstanceID");

        var nodeHostName = svg.append("g").append("text")
                .attr("class", "node-hostname")
                .attr("y", "1em")
                .attr("x", "-8em")
                .attr('opacity', '0')
                .attr('text-anchor', 'left')
                .text("HostName");

        var ports = svg.append("g").append("text")
                .attr("class", "node-ports")
                .attr("y", "2em")
                .attr("x", "-8em")
                .attr('opacity', '0')
                .attr('text-anchor', 'left')
                .text("Ports");

        var g = svg.selectAll(".arc")
                .data(pie(data), function(d) {return d.data.id + '_' + d.data.status;})
                .enter().append("g")
                .attr("class", "arc");

        g.append("path")
                .each(function(d) {d.outerRadius = outerRadius - 20})
                .attr("d", arc)
                .style("fill", function (d) {
                    return color(d.data.status);
                })
                .on('mouseover', onMouseOver(outerRadius, 0))
                .on('mouseout', onMouseOut(outerRadius - 20, 150));

        g.append("text")
                .attr("transform", function (d) {
                    return "translate(" + arc.centroid(d) + ")";
                })
                .attr("dy", ".35em")
                .style("text-anchor", "middle")
                .text(function (d) {
                    return d.data['dataCenterInfo']['zone'];
                });


        function onMouseOver(outerRadius, delay) {
            return function(d) {
                d3.select(this).attr('cursor', 'pointer');
                showServerDetails(d);
                d3.select(this).transition().delay(delay).attrTween("d", function(d) {
                    var i = d3.interpolate(d.outerRadius, outerRadius);
                    return function(t) { d.outerRadius = i(t); return arc(d); };
                });
            };
        }

        function onMouseOut(outerRadius, delay) {
            return function(d) {
                d3.select(this).attr('cursor', 'pointer');
                hideServerDetails(d);
                d3.select(this).transition().delay(delay).attrTween("d", function(d) {
                    var i = d3.interpolate(d.outerRadius, outerRadius);
                    return function(t) { d.outerRadius = i(t); return arc(d); };
                });
            };
        }

        function showServerDetails(d) {
            nodeStatus.attr('opacity', 1).text("Status : " + d.data['status']);
            nodeVip.attr('opacity', 1).text("VIP : " + d.data['vipAddress']);
            nodeId.attr('opacity', 1).text("InstanceId : " + d.data['dataCenterInfo']['instanceId']);
            nodeHostName.attr('opacity', 1).text("Host : " + d.data['dataCenterInfo']['publicAddress']['hostName']);

            var portsList = "";
            d.data['ports'].forEach(function(portObj, i) {
                if (portObj.port) {
                    if (i != 0) {
                        portsList = portsList + "," + portObj.port;
                    } else {
                        portsList = portObj.port;
                    }
                }
            });
            if (portsList) {
                ports.attr('opacity', 1).text("Ports : " + portsList);
            }
        };

        function hideServerDetails(d) {
            nodeStatus.attr('opacity', 0);
            nodeVip.attr('opacity', 0);
            nodeId.attr('opacity', 0);
            nodeHostName.attr('opacity', 0);
            ports.attr('opacity', 0);
        }

    }

    function injectStubValue(clusterInfo) {
        for (var i = 0; i < clusterInfo.length; i++) {
            clusterInfo[i].value = 100;
        }
    }

    return {
        render: render,
        loadData : loadData
    }
}

$(function() {

    var writeClusterChart = new ClusterStatusChart({containerId : "write-cluster"});
    var readClusterChart = new ClusterStatusChart({containerId : "read-cluster"});

    $(window).on('RenderClusterStatusChart', function(e, data) {
        writeClusterChart.loadData(filterEurekaServers(data.clustersInfo, ['eureka2-write', 'eureka2_write'])).render();
        readClusterChart.loadData(filterEurekaServers(data.clustersInfo, ['eureka2-read', 'eureka2_read'])).render();

    });


    function filterEurekaServers(clustersInfo, appNamePrefixList) {
        var eurekaSvrList = [], appName;
        clustersInfo.forEach(function(eurekaSvr) {
            appName = eurekaSvr.app.toLowerCase();
            appNamePrefixList.forEach(function(appNamePrefix) {
                if (appName.indexOf(appNamePrefix) > -1) {
                    eurekaSvrList.push(eurekaSvr);
                }
            });
        });
        return eurekaSvrList;
    }

});
