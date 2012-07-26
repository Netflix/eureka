<%@ page language="java" import="java.util.*,java.io.*,com.netflix.discovery.*,com.netflix.discovery.util.*" pageEncoding="UTF-8" %>
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
    
    <title>Discovery - <%=ContainerContext.VERSION%> - Table View</title>
    <link rel="stylesheet" type="text/css" href="./css/main.css">
  </head>
  
  <body id="four">
    <jsp:include page="header.jsp" />
    <jsp:include page="navbar.jsp" />
    <div>
      <iframe src="./flex/Discovery.html" width="100%" height="100%">
      </iframe> 
    </div>  
  </body>
</html>
