<%@ page language="java" import="java.util.*,java.util.Map.Entry,com.netflix.discovery.shared.Pair,
com.netflix.discovery.shared.*,com.netflix.eureka.util.*,com.netflix.appinfo.InstanceInfo.*,
com.netflix.appinfo.DataCenterInfo.*,com.netflix.appinfo.AmazonInfo.MetaDataKey,com.netflix.eureka.resources.*,
com.netflix.eureka.*,com.netflix.appinfo.*,com.netflix.eureka.util.StatusUtil" pageEncoding="UTF-8" %>
<%
String path = request.getContextPath();
String basePath = request.getScheme()+"://"+request.getServerName()+":"+request.getServerPort()+path+"/";
%>

<!DOCTYPE HTML PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN">

<html>
  <head>
    <base href="<%=basePath%>">

    <title>Eureka</title>
    <link rel="stylesheet" type="text/css" href="./css/main.css">
    <script type="text/javascript" src="./js/jquery-1.11.1.js" ></script>
    <script type="text/javascript" src="./js/jquery.dataTables.js" ></script>
    <script type="text/javascript" >
       $(document).ready(function() {
           $('table.stripeable tr:odd').addClass('odd');
           $('table.stripeable tr:even').addClass('even');
           $('#instances thead th').each(function () {
               var title = $('#instances thead th').eq($(this).index()).text();
               $(this).html(title + '</br><input type="text" placeholder="Search ' + title + '" />');
           });
           // DataTable
           var table = $('#instances').DataTable({"paging": false, "bInfo": false, "sDom": 'ltipr', "bSort": false});
           // Apply the search
           table.columns().eq(0).each(function (colIdx) {
               $('input', table.column(colIdx).header()).on('keyup change', function () {
                   table.column(colIdx).search(this.value).draw();
               });
           });
       });
    </script>
  </head>
  
  <body id="one">
    <jsp:include page="header.jsp" />
    <jsp:include page="navbar.jsp" />
    <div id="content">
      <div class="sectionTitle">Instances currently registered with Eureka</div>
        <table id='instances' class="stripeable">
           <thead><tr><th>Application</th><th>AMIs</th><th>Availability Zones</th><th>Status</th></tr></thead>
           <tfoot><tr><th>Application</th><th>AMIs</th><th>Availability Zones</th><th>Status</th></tr></tfoot>
           <tbody>
           <%
           EurekaServerContext serverContext = (EurekaServerContext) pageContext.getServletContext()
                   .getAttribute(EurekaServerContext.class.getName());
           for(Application app : serverContext.getRegistry().getSortedApplications()) {
               out.print("<tr><td><b>" + app.getName() + "</b></td>");
               Map<String, Integer> amiCounts = new HashMap<String, Integer>();
               Map<InstanceStatus,List<Pair<String, String>>> instancesByStatus =
                   new HashMap<InstanceStatus, List<Pair<String,String>>>();
               Map<String,Integer> zoneCounts = new HashMap<String, Integer>();
               
               for(InstanceInfo info : app.getInstances()){
                   String id = info.getId();
                   String url = info.getStatusPageUrl();
                   InstanceStatus status = info.getStatus();
                   String ami = "n/a";
                   String zone = "";
                   if(info.getDataCenterInfo().getName() == Name.Amazon){
                       AmazonInfo dcInfo = (AmazonInfo)info.getDataCenterInfo();
                       ami = dcInfo.get(MetaDataKey.amiId);
                       zone = dcInfo.get(MetaDataKey.availabilityZone);
                   }
                   
                   Integer count = amiCounts.get(ami);
                   if(count != null){
                       amiCounts.put(ami, Integer.valueOf(count.intValue()+1));
                   }else {
                       amiCounts.put(ami, Integer.valueOf(1));
                   }
                   
                   count = zoneCounts.get(zone);
                   if(count != null){
                       zoneCounts.put(zone, Integer.valueOf(count.intValue()+1));
                   }else {
                       zoneCounts.put(zone, Integer.valueOf(1));
                   }
                   List<Pair<String, String>> list = instancesByStatus.get(status);
                   
                   if(list == null){
                       list = new ArrayList<Pair<String,String>>();
                       instancesByStatus.put(status, list);
                   }
                   list.add(new Pair<String, String>(id, url));  
               }
               StringBuilder buf = new StringBuilder();
               for (Iterator<Entry<String, Integer>> iter = 
                   amiCounts.entrySet().iterator(); iter.hasNext();) {
                   Entry<String, Integer> entry = iter.next();
                   buf.append("<b>").append(entry.getKey()).append("</b> (").append(entry.getValue()).append("), ");
               }
               out.println("<td>" + buf.toString() + "</td>");
               buf = new StringBuilder();
               for (Iterator<Entry<String, Integer>> iter = 
                   zoneCounts.entrySet().iterator(); iter.hasNext();) {
                   Entry<String, Integer> entry = iter.next();
                   buf.append("<b>").append(entry.getKey()).append("</b> (").append(entry.getValue()).append("), ");
               }
               out.println("<td>" + buf.toString() + "</td>");
               buf = new StringBuilder();
               for (Iterator<Entry<InstanceStatus, List<Pair<String,String>>>> iter = 
                   instancesByStatus.entrySet().iterator(); iter.hasNext();) {
                   Entry<InstanceStatus, List<Pair<String,String>>> entry = iter.next();
                   List<Pair<String, String>> value = entry.getValue();
                   InstanceStatus status = entry.getKey();
                   if(status != InstanceStatus.UP){
                       buf.append("<font color=red size=+1><b>");
                   }
                   buf.append("<b>").append(status.name()).append("</b> (").append(value.size()).append(") - ");
                   if(status != InstanceStatus.UP){
                       buf.append("</font></b>");
                   }
                   
                   for(Pair<String,String> p : value) {
                       String id = p.first();
                       String url = p.second();
                       if(url != null && url.startsWith("http")){
                           buf.append("<a href=\"").append(url).append("\">");
                       }else {
                           url = null;
                       }
                       buf.append(id);
                       if(url != null){
                           buf.append("</a>");
                       }
                       buf.append(", ");
                   }
               }
               out.println("<td>" + buf.toString() + "</td></tr>");
           }
           %>
           </tbody>
           </table>
      </div>
      <div>
      <div class="sectionTitle">General Info</div>
      <table id='generalInfo' class="stripeable">
          <tr><th>Name</th><th>Value</th></tr>
           <%
           StatusInfo statusInfo = (new StatusUtil(serverContext)).getStatusInfo();
           Map<String,String> genMap = statusInfo.getGeneralStats();
           for (Map.Entry<String,String> entry : genMap.entrySet()) {
             out.print("<tr>");
             out.print("<td>" + entry.getKey() +  "</td><td>" + entry.getValue() + "</td>");
             out.print("</tr>");
           }
           Map<String,String> appMap = statusInfo.getApplicationStats();
           for (Map.Entry<String,String> entry : appMap.entrySet()) {
             out.print("<tr>");
             out.print("<td>" + entry.getKey() +  "</td><td>" + entry.getValue() + "</td>");
             out.print("</tr>");
           }
           %>
           </table>      
      </div>
      <div>
      <div class="sectionTitle">Instance Info</div>
        <table id='instanceInfo' class="stripeable">
          <tr><th>Name</th><th>Value</th></tr>
           <%
           InstanceInfo instanceInfo = statusInfo.getInstanceInfo();
           Map<String,String> instanceMap = new HashMap<String,String>();
           instanceMap.put("ipAddr", instanceInfo.getIPAddr());
           instanceMap.put("status", instanceInfo.getStatus().toString());
           if(instanceInfo.getDataCenterInfo().getName() == DataCenterInfo.Name.Amazon) {
               AmazonInfo info = (AmazonInfo) instanceInfo.getDataCenterInfo();
               instanceMap.put("availability-zone", info.get(AmazonInfo.MetaDataKey.availabilityZone));
               instanceMap.put("public-ipv4", info.get(AmazonInfo.MetaDataKey.publicIpv4));
               instanceMap.put("instance-id", info.get(AmazonInfo.MetaDataKey.instanceId));
               instanceMap.put("public-hostname", info.get(AmazonInfo.MetaDataKey.publicHostname));
               instanceMap.put("ami-id", info.get(AmazonInfo.MetaDataKey.amiId));
               instanceMap.put("instance-type", info.get(AmazonInfo.MetaDataKey.instanceType));
           }
           for (Map.Entry<String,String> entry : instanceMap.entrySet()) {
             out.print("<tr>");
             out.print("<td>" + entry.getKey() +  "</td><td>" + entry.getValue() + "</td>");
             out.print("</tr>");
           }
           %>
           </table>
    </div>

  </body>
</html>
