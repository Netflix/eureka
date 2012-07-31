<%@ page language="java" 
  import="java.util.*,java.net.*,com.netflix.config.*, com.netflix.discovery.*,com.netflix.discovery.resources.*,com.netflix.discovery.cluster.*,com.netflix.appinfo.*" pageEncoding="UTF-8" %>
<div id="navcontainer">
<ul id="navlist">
<li class="one"><a href="jsp/status.jsp">Home</a></li>
<li class="two"><a href="jsp/tracercounter.jsp">Tracers, Counters &amp; Errors</a></li>
<li class="three"><a href="jsp/lastN.jsp">Last 1000 since startup</a></li>
<li class="four"><a href="jsp/tableView.jsp">Table View</a></li>
</ul>
</div>
  
  <dt> &nbsp;</dt> 
  <dd><b>DS Replicas: <b>
  <%
   List<ReplicaNode> list = ReplicaAwareInstanceRegistry.getInstance().getReplicaNodes();
   int i=0;
   for(ReplicaNode node : list){
     try{
        URI uri = new URI(node.getServiceUrl());
        String href = "http://" + uri.getHost() + ":" + uri.getPort() + "/discovery/";
        out.print("<span class=\"hlist\"><a href=\"" + href + "\">" + uri.getHost() + "</a></span>");
     }catch(Exception e){
     }
   }   
  %>
  </dd>
