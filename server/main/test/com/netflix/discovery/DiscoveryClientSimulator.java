/*
 * DiscoveryClientSimulator.java
 *
 * $Header: $
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.apache.commons.configuration.Configuration;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.AmazonInfo.MetaDataKey;
import com.netflix.config.NetflixConfiguration;
import com.netflix.library.NFLibraryHelper;
import com.netflix.library.NFLibraryManager;
import com.netflix.platform.core.PlatformManager;
import com.netflix.registry.resource.ApplicationEnum;


/**
 * Simple client simulator that registers clients and sends heartbeats
 *
 * @author gkim
 */
public class DiscoveryClientSimulator {


  private final static Random sRandomGenerator = new Random();
  private final static String[] ZONES = {"us-east-1c","us-east-1a","us-east-1b","us-west-1a"};
  private final static String[] SIZES = {"m1.large","m1.medium","m1.small"};


  private static InstanceInfo createInstance(ApplicationEnum app, int num){
      InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
      String id = "domU-"+ num + "-"+app.ordinal()+"-39-02-94-A1.compute-1.internal";
      builder.setAppName(app).
          setHostName(id).
          setIPAddr("67.202.67."+num).
          setPort(80).
          setSID(String.valueOf(num)).
          setSourceVersion("version-"+ sRandomGenerator.nextInt(10)).
          setVersion("v1").
          setDataCenterInfo(AmazonInfo.Builder.newBuilder().
                  addMetadata(MetaDataKey.publicHostname, "ec2-"+num+"-"+app.ordinal()+"-168-6.compute-1.amazonaws.com").
                  addMetadata(MetaDataKey.instanceId, "i-"+app.ordinal()+"ed05f"+num).
                  addMetadata(MetaDataKey.instanceType, SIZES[sRandomGenerator.nextInt(10)%SIZES.length]).
                  addMetadata(MetaDataKey.availabilityZone, ZONES[sRandomGenerator.nextInt(10)%ZONES.length]).build()).
          add("appSpecificKey1", "value1").add("appSpecificKey2", "value2");
      return builder.build();
  }

  public static void main(String[] args) throws Exception{

      Properties props = System.getProperties();
      System.out.println( "Java Runtime Environment version:               "+props.get("java.version"));
      System.out.println( "Java Virtual Machine specification version:     "+props.get("java.vm.specification.version"));
      System.out.println( "Java Virtual Machine implementation version:    "+props.get("java.specification.version"));
      System.out.println( "Java Runtime Environment specification version: "+props.get("java.specification.version"));

      try{
          String netflixEnv = System.getProperty("netflix.environment", "test");

          //Set the env in System props (require by platform)
          System.setProperty("netflix.environment", netflixEnv);

          // Load system properties:
          NFLibraryHelper.loadCascadedConfiguration("discovery", null, false);
          Configuration config = NetflixConfiguration.getInstance().
          getConfiguration("discovery");
          props = NetflixConfiguration.getProperties(config);
          props.setProperty("netflix.discovery.serviceUrl.us-east-1a", "http://ec2-174-129-99-217.compute-1.amazonaws.com:7001/discovery/v1/");
          props.setProperty("netflix.discovery.serviceUrl.us-east-1b", "http://ec2-174-129-99-217.compute-1.amazonaws.com:7001/discovery/v1/");
          props.setProperty("netflix.discovery.serviceUrl.us-east-1c", "http://ec2-174-129-99-217.compute-1.amazonaws.com:7001/discovery/v1/");
          props.setProperty("netflix.discovery.serviceUrl.us-east-1d", "http://ec2-174-129-99-217.compute-1.amazonaws.com:7001/discovery/v1/");
          NFLibraryManager.initLibrary(PlatformManager.class,
                  props,
                  true,
                  false);

      }catch(Exception e) {
          //Bomb
          throw new RuntimeException(e);
      }
      List<DiscoveryClient> clients = new ArrayList<DiscoveryClient>();
 //     for(ApplicationEnum app : ApplicationEnum.values()){
          for(int num=0; num < 2; num++){
              clients.add(new DiscoveryClient(createInstance(ApplicationEnum.NCCP, num)));
          }
   //   }
      while (true){
          Thread.sleep(100000);
      }
  }
}
