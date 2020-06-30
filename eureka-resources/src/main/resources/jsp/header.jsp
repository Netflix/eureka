<%@ page language="java" 
  import="java.util.*,com.netflix.config.*,com.netflix.eureka.*,com.netflix.eureka.registry.*,com.netflix.eureka.resources.*,com.netflix.appinfo.*,com.netflix.eureka.util.*" pageEncoding="UTF-8" %>

<%@page import="com.netflix.appinfo.AmazonInfo.MetaDataKey"%>
<div id="header">
  <%
  EurekaServerContext serverContext = (EurekaServerContext) pageContext.getServletContext()
          .getAttribute(EurekaServerContext.class.getName());
  InstanceInfo selfInstanceInfo = serverContext.getApplicationInfoManager().getInfo();
  DataCenterInfo info = selfInstanceInfo.getDataCenterInfo();
  PeerAwareInstanceRegistry registry =serverContext.getRegistry();
  AmazonInfo amazonInfo = null;
  if(info.getName() == DataCenterInfo.Name.Amazon) {
      amazonInfo = (AmazonInfo)info;
  }
  if(amazonInfo != null) {
      out.print(" EUREKA SERVER (AMI: " + amazonInfo.get(AmazonInfo.MetaDataKey.amiId) +")");
  }
  out.print("</h3>");
  %>
  <h4 id="uptime"><font size="+1" color="red"><b>Environment: <%= ConfigurationManager.getDeploymentContext().getDeploymentEnvironment() %></b></font>, Data center: <%= ConfigurationManager.getDeploymentContext().getDeploymentDatacenter() %></h4>
  <%
  if(amazonInfo != null) {  
     out.print("<h4 id=\"uptime\">Zone: " + amazonInfo.get(AmazonInfo.MetaDataKey.availabilityZone) + ", instance-id: " + amazonInfo.get(AmazonInfo.MetaDataKey.instanceId));
  }
  %>
  <h4 id="uptime">Current time: <%=StatusResource.getCurrentTimeAsString() %>, Uptime: <%=StatusInfo.getUpTime()%></h4>
  <hr id="uptime">Lease expiration enabled: <%=registry.isLeaseExpirationEnabled() %>, Renews threshold: <%=registry.getNumOfRenewsPerMinThreshold() %>, Renews (last min):  <%=registry.getNumOfRenewsInLastMin() %></hr>
  <% if (registry.isBelowRenewThresold() == 1) {
 	  if (!registry.isSelfPreservationModeEnabled()) {
   %>
  <h4 id="uptime"><font size="+1" color="red"><b>RENEWALS ARE LESSER THAN THE THRESHOLD.THE SELF PRESERVATION MODE IS TURNED OFF.THIS MAY NOT PROTECT INSTANCE EXPIRY IN CASE OF NETWORK/OTHER PROBLEMS.</b></font></h4>
   <%} else {%>
 	 <h4 id="uptime"><font size="+1" color="red"><b>EMERGENCY! EUREKA MAY BE INCORRECTLY CLAIMING INSTANCES ARE UP WHEN THEY'RE NOT. RENEWALS ARE LESSER THAN THRESHOLD AND HENCE THE INSTANCES ARE NOT BEING EXPIRED JUST TO BE SAFE.</b></font></h4>
   <%} %>
   <%} else if (!registry.isSelfPreservationModeEnabled()) {
   	%>
    <h4 id="uptime"><font size="+1" color="red"><b>THE SELF PRESERVATION MODE IS TURNED OFF.THIS MAY NOT PROTECT INSTANCE EXPIRY IN CASE OF NETWORK/OTHER PROBLEMS.</b></font></h4>
    <%}%>
  </h4>
  <% if (!registry.shouldAllowAccess(false)) { %>
    <h4 id="uptime"><font size="+1" color="red"><b>This server is not allowing registry fetch for local registry.</b></font></h4>
  <% } else if (!registry.shouldAllowAccess(true)) { %>
    <h4 id="uptime"><font size="+1" color="red"><b>This server is not allowing registry fetch for remote registry.</b></font></h4>
  <%}%>
</div>
