package com.netflix.discovery;

import java.util.Properties;

import junit.framework.Assert;

import org.junit.Test;

import com.netflix.discovery.util.EIPManager;
import com.netflix.library.NFLibraryManager;
import com.netflix.platform.core.PlatformManager;

public class TestEIPManager {
    @Test
    public void basicTest() throws Throwable{
         try{
             
             Properties props = new Properties();
             props.setProperty("netflix.appinfo.name", "testApp");
             props.setProperty("netflix.appinfo.sid", "120");
             props.setProperty("netflix.appinfo.version", "prod-424566");
             props.setProperty("netflix.appinfo.port", "7001");
             props.setProperty("netflix.discovery.eip.us-east-1c","75.101.165.111,204.236.228.165");
             props.setProperty("platform.ListOfComponentsToInit", "LOGGING,APPINFO,DISCOVERY" );
             
             NFLibraryManager.initLibrary(PlatformManager.class,
                     props,
                     true,
                     false); 
             
             
             EIPManager manager = EIPManager.getInstance();
             
             String candidateEIP = manager.getCandidateEIP("i-xxxxxx", "us-east-1c", "127.0.0.1");
             Assert.assertNotNull(candidateEIP);
             System.out.println("\n\n\n\n\n\nFound canidateEIP: " + candidateEIP);
             
             
             NFLibraryManager.shutdownLibrary(PlatformManager.class);
             
         } catch (Exception e) {
             e.printStackTrace();
         }  
     }
    
    @Test
    public void testOnA() throws Throwable{
         try{
             
             Properties props = new Properties();
             props.setProperty("netflix.appinfo.name", "testApp");
             props.setProperty("netflix.appinfo.sid", "120");
             props.setProperty("netflix.appinfo.version", "prod-424566");
             props.setProperty("netflix.appinfo.port", "7001");
             props.setProperty("netflix.discovery.eip.us-east-1a","");
             props.setProperty("platform.ListOfComponentsToInit", "LOGGING,APPINFO,DISCOVERY" );
             
             NFLibraryManager.initLibrary(PlatformManager.class,
                     props,
                     true,
                     false); 
             
             
             EIPManager manager = EIPManager.getInstance();
             
             String candidateEIP = manager.getCandidateEIP("i-xxxxxx", "us-east-1a", "127.0.0.1");
             Assert.assertNull(candidateEIP);
             System.out.println("\n\n\n\n\n\nFound canidateEIP: " + candidateEIP);
             
             
             NFLibraryManager.shutdownLibrary(PlatformManager.class);
             
         } catch (Exception e) {
             e.printStackTrace();
         }  
     }
    
    @Test
    public void testOnB() throws Throwable{
         try{
             
             Properties props = new Properties();
             props.setProperty("netflix.appinfo.name", "testApp");
             props.setProperty("netflix.appinfo.sid", "120");
             props.setProperty("netflix.appinfo.version", "prod-424566");
             props.setProperty("netflix.appinfo.port", "7001");
             props.setProperty("netflix.discovery.eip.us-east-1c","75.101.165.111,204.236.228.165");
             props.setProperty("platform.ListOfComponentsToInit", "LOGGING,APPINFO,DISCOVERY" );
             
             NFLibraryManager.initLibrary(PlatformManager.class,
                     props,
                     true,
                     false); 
             
             
             EIPManager manager = EIPManager.getInstance();
             
             String candidateEIP = manager.getCandidateEIP("i-xxxxxx", "us-east-1b", "127.0.0.1");
             Assert.assertNull(candidateEIP);
             System.out.println("\n\n\n\n\n\nFound canidateEIP: " + candidateEIP);
             
             
             NFLibraryManager.shutdownLibrary(PlatformManager.class);
             
         } catch (Exception e) {
             e.printStackTrace();
         }  
     }
    
    @Test
    public void testOnD() throws Throwable{
         try{
             
             Properties props = new Properties();
             props.setProperty("netflix.appinfo.name", "testApp");
             props.setProperty("netflix.appinfo.sid", "120");
             props.setProperty("netflix.appinfo.version", "prod-424566");
             props.setProperty("netflix.appinfo.port", "7001");
             props.setProperty("netflix.discovery.eip.us-east-1d","204.236.228.170,174.129.223.55");
             props.setProperty("platform.ListOfComponentsToInit", "LOGGING,APPINFO,DISCOVERY" );
             
             NFLibraryManager.initLibrary(PlatformManager.class,
                     props,
                     true,
                     false); 
             
             
             EIPManager manager = EIPManager.getInstance();
             
             String candidateEIP = manager.getCandidateEIP("i-xxxxxx", "us-east-1d", "127.0.0.1");
             Assert.assertNotNull(candidateEIP);
             System.out.println("\n\n\n\n\n\nFound canidateEIP: " + candidateEIP);
             
             
             NFLibraryManager.shutdownLibrary(PlatformManager.class);
             
         } catch (Exception e) {
             e.printStackTrace();
         }  
     }

}
