<%@ page language="java" 
  import="java.util.*,java.net.*,com.netflix.config.*, com.netflix.eureka.*,com.netflix.eureka.resources.*,com.netflix.eureka.cluster.*,com.netflix.appinfo.*" pageEncoding="UTF-8" %>
<div id="navcontainer">
<ul id="navlist">
<li class="one"><a href="jsp/status.jsp">Home</a></li>
<li class="three"><a href="jsp/lastN.jsp">Last 1000 since startup</a></li>
</ul>
</div>
  
  <dt> &nbsp;</dt> 
  <dd><b>DS Replicas: <b>
  <%
   EurekaServerContext serverContext = (EurekaServerContext) pageContext.getServletContext()
          .getAttribute(EurekaServerContext.class.getName());
   List<PeerEurekaNode> list = serverContext.getPeerEurekaNodes().getPeerNodesView();
   int i=0;
   for(PeerEurekaNode node : list){
     try{
        URI uri = new URI(node.getServiceUrl());
        String href = "http://" + uri.getHost() + ":" + uri.getPort() + request.getContextPath();
        out.print("<span class=\"hlist\"><a href=\"" + href + "\">" + uri.getHost() + "</a></span>");
     }catch(Exception e){
     }
   }   
  %>
  </dd>
