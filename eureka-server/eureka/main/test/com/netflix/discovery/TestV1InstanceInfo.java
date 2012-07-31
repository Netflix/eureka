package com.netflix.discovery;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Map.Entry;

import junit.framework.Assert;

import org.junit.Test;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.appinfo.DataCenterInfo.Name;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.converters.JsonXStream;
import com.netflix.discovery.converters.XmlXStream;
import com.netflix.discovery.shared.Application;
import com.netflix.library.NFLibraryManager;
import com.netflix.platform.core.PlatformManager;
import com.netflix.registry.resource.ApplicationEnum;
import com.netflix.util.Pair;
import com.thoughtworks.xstream.XStream;

public class TestV1InstanceInfo {
    @Test
    public void baseicTest() throws Throwable{
         try{
             
             Properties props = new Properties();
             props.setProperty("netflix.appinfo.name", "testApp");
             props.setProperty("netflix.appinfo.sid", "120");
             props.setProperty("netflix.appinfo.version", "prod-424566");
             props.setProperty("netflix.appinfo.port", "7001");
             props.setProperty("netflix.environment", "test");
             props.setProperty("platform.ListOfComponentsToInit", "LOGGING,APPINFO,DISCOVERY" );
             
             NFLibraryManager.initLibrary(PlatformManager.class,
                     props,
                     true,
                     false); 
           
             Application app = 
                 DiscoveryManager.getInstance().getLookupService().getApplication(ApplicationEnum.DISCOVERY);
             InstanceInfo instance1 = app.getInstances().get(0);
             instance1.setStatus(InstanceStatus.OUT_OF_SERVICE);
             
             
             InstanceInfo instance2 = app.getInstances().get(1);
             instance2.setStatus(InstanceStatus.UP);
             
             InstanceInfo instance3 = app.getInstances().get(2);
             instance3.setStatus(InstanceStatus.UNKNOWN);
             
             InstanceInfo instance4 = app.getInstances().get(3);
             instance4.setStatus(InstanceStatus.DOWN);
             
             //Register V1 aware converter for InstanceInfo
             JsonXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(),
                     XStream.PRIORITY_VERY_HIGH);
             XmlXStream.getInstance().registerConverter(new V1AwareInstanceInfoConverter(),
                     XStream.PRIORITY_VERY_HIGH);
             
             XStream xstream = XmlXStream.getInstance();
             InstanceInfo instances[] = new InstanceInfo[]{
                     instance1, instance2, instance3, instance4
             };

             for(InstanceInfo instance : instances){
                 CurrentRequestVersion.set(Version.V1);

                 String xml = xstream.toXML(instance);
                 System.out.println("V1 ==> " + xml);

                 InstanceInfo info = (InstanceInfo)xstream.fromXML(xml);
                 
                 Assert.assertEquals(info.getStatus(), 
                         (instance.getStatus() == InstanceStatus.OUT_OF_SERVICE ||
                          instance.getStatus() == InstanceStatus.UNKNOWN) ? InstanceStatus.DOWN :
                              instance.getStatus());
                // Assert.assertEquals(xml, xstream.toXML(info));

                 CurrentRequestVersion.set(Version.V2);
                 xml = xstream.toXML(instance);
                 System.out.println("V2 ==> " + xml);

                 info = (InstanceInfo)xstream.fromXML(xml);
                 
                 Assert.assertEquals(info.getStatus(), instance.getStatus());
                  // Assert.assertEquals(xml, xstream.toXML(info));
             }
                   
             NFLibraryManager.shutdownLibrary(PlatformManager.class);
             
         } catch (Exception e) {
             e.printStackTrace();
         }  
     }
}
