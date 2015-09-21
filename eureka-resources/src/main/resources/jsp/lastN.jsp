<%@ page language="java" import="java.util.*,java.io.*,com.netflix.eureka.*,com.netflix.eureka.registry.*,com.netflix.eureka.util.*" pageEncoding="UTF-8" %>
<%
    String path = request.getContextPath();
    String basePath = request.getScheme() + "://"
            + request.getServerName() + ":" + request.getServerPort()
            + path + "/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<%@page import="com.netflix.discovery.shared.Pair"%><html>
  <head>
    <base href="<%=basePath%>">

    <title>Eureka - Last N events</title>
    <link rel="stylesheet" type="text/css" href="./css/main.css">
    <link type="text/css" href="./css/jquery-ui-1.7.2.custom.css" rel="Stylesheet" />
    <script type="text/javascript" src="./js/jquery-1.11.1.js" ></script>
    <script type="text/javascript" src="./js/jquery-ui-1.7.2.custom.min.js"></script>
    <script type="text/javascript" >
       $(document).ready(function() {
           $('table.stripeable tr:odd').addClass('odd');
           $('table.stripeable tr:even').addClass('even');
           $("#tabs").tabs();
           });
    </script>
  </head>
  
  <body id="three">
    <jsp:include page="header.jsp" />
    <jsp:include page="navbar.jsp" />
    <div id="content">
	<div id="tabs">
	<ul>
	    <li><a href="#tabs-1">Last 1000 canceled leases</a></li>
	    <li><a href="#tabs-2">Last 1000 newly registered leases</a></li>
	</ul>
    <div id="tabs-1">
      <%
      EurekaServerContext serverContext = (EurekaServerContext) pageContext.getServletContext()
              .getAttribute(EurekaServerContext.class.getName());
      PeerAwareInstanceRegistry registry = serverContext.getRegistry();
      List<Pair<Long, String>> list = registry.getLastNCanceledInstances();
      out.print("<table id=\'lastNCanceled\' class=\"stripeable\">");
      out.print("<tr><th>Timestamp</th><th>Lease</th></tr>");
      for (Pair<Long, String> entry : list) {
          out.print("<tr><td>" + (new Date(entry.first().longValue()))
                  + "</td><td>" + entry.second() + "</td></tr>");
      }
      out.println("</table>");
     %>
    </div>
    <div id="tabs-2">
      <%
      list = registry.getLastNRegisteredInstances();
      out.print("<table id=\'lastNRegistered\' class=\"stripeable\">");
      out.print("<tr><th>Timestamp</th><th>Lease</th></tr>");
      for (Pair<Long, String> entry : list) {
          out.print("<tr><td>" + (new Date(entry.first().longValue()))
                  + "</td><td>" + entry.second() + "</td></tr>");
      }
      out.println("</table>");
     %>
    </div>
  </div>
  </div>
  </body>
</html>
