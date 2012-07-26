/*
 * ProtobufUtils.java
 *  
 * $Header: $ 
 * $DateTime: $
 *
 * Copyright (c) 2009 Netflix, Inc.  All rights reserved.
 */
package com.netflix.discovery.converters;

import com.netflix.appinfo.DataCenterInfo.Name;

import com.netflix.appinfo.AmazonInfo;
import com.netflix.appinfo.AppInfoProtos;
import com.netflix.appinfo.DataCenterInfo;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.LeaseInfo;
import com.netflix.appinfo.AppInfoProtos.Property;
import com.netflix.appinfo.AppInfoProtos.Instance.Lease;
import com.netflix.appinfo.AppInfoProtos.Instance.Status;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Application;
import com.netflix.discovery.shared.Applications;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;


/**
 * Some useful static utilities 
 */
public class ProtobufUtils {

    
    public static AppInfoProtos.Applications marshalAppsProto(Applications apps){
        AppInfoProtos.Applications.Builder builder =
            AppInfoProtos.Applications.newBuilder();
        
        for(Application app : apps.getRegisteredApplications()){
            builder.addApplication(marshalAppProto(app));
        }
        return builder.build();
    }
    
    public static Applications unmarshalAppsProto(AppInfoProtos.Applications proto) {
        Applications apps = new Applications();
        
        for(AppInfoProtos.Application app : proto.getApplicationList()) {
            apps.addApplication(unmarshalAppProto(app));
        }
        return apps;
    }
    
    public static AppInfoProtos.Application marshalAppProto(Application app){
        AppInfoProtos.Application.Builder builder =
            AppInfoProtos.Application.newBuilder();
        builder.setName(app.getName());
        
        for(InstanceInfo instance : app.getInstances()){
            builder.addInstance(marshalInstanceProto(instance));
        }
        return builder.build();
    }
    
    public static Application unmarshalAppProto(AppInfoProtos.Application proto) {
        Application app = new Application();
        app.setName(proto.getName());
        
        for(AppInfoProtos.Instance instance : proto.getInstanceList()){
            app.addInstance(unmarshalInstanceProto(instance));
        }
        return app;
    }

    public static AppInfoProtos.Instance marshalInstanceProto(InstanceInfo info) {
        AppInfoProtos.Instance.Builder builder =
            AppInfoProtos.Instance.newBuilder();

        builder.setApp(info.getAppName()).
            setHostName(info.getHostName()).
            setVersion(info.getVersion()).
            setSid(info.getSID()).
            setSourceVersion(info.getSourceVersion()).
            setStatus(Status.valueOf(info.getStatus().name())).
            setPort(info.getPort()).
            setIpAddr(info.getIPAddr());
        

        AppInfoProtos.Instance.DataCenter.Builder dcBuilder =
            AppInfoProtos.Instance.DataCenter.newBuilder();
       
        dcBuilder.setName(AppInfoProtos.Instance.DataCenter.Name.valueOf(
                info.getDataCenterInfo().getName().ordinal()));
        
        if(info.getDataCenterInfo().getName() == Name.Amazon) {
            for(Entry<String, String> entry : 
                ((AmazonInfo)info.getDataCenterInfo()).getMetadata().entrySet()){
                dcBuilder.addProperty(AppInfoProtos.Property.newBuilder().
                        setKey(entry.getKey()).
                        setValue(entry.getValue()).build());
            }
        }
        builder.setDataCenter(dcBuilder.build());
        
        if(info.getLeaseInfo() != null){
            AppInfoProtos.Instance.Lease.Builder leaseBuilder =
                AppInfoProtos.Instance.Lease.newBuilder();
            LeaseInfo leaseInfo = info.getLeaseInfo();
            leaseBuilder.setClock(leaseInfo.getClock()).
                setEvictionDurationInSecs(leaseInfo.getDurationInSecs()).
                setEvictionTimestamp(leaseInfo.getEvictionTimestamp()).
                setLastRenewalTimestamp(leaseInfo.getRenewalTimestamp()).
                setRegistrationTimestamp(leaseInfo.getRegistrationTimestamp()).
                setRenewalIntervalInSecs(leaseInfo.getRenewalIntervalInSecs());
            builder.setLease(leaseBuilder.build());
        }
        
        for(Entry<String, String> entry : 
            info.getMetadata().entrySet()){
            builder.addProperty(AppInfoProtos.Property.newBuilder().
                    setKey(entry.getKey()).
                    setValue(entry.getValue()).build());
        }
        
        return builder.build();
    }

    public static InstanceInfo unmarshalInstanceProto(AppInfoProtos.Instance proto) {
        InstanceInfo.Builder builder = InstanceInfo.Builder.newBuilder();
        
        builder.setAppName(proto.getApp()).
            setHostName(proto.getHostName()).
            setVersion(proto.getVersion()).
            setSID(proto.getSid()).
            setSourceVersion(proto.getSourceVersion()).
            setStatus(InstanceStatus.valueOf(proto.getStatus().name())).
            setPort(proto.getPort()).
            setIPAddr(proto.getIpAddr());
    
        DataCenterInfo dcInfo = InstanceInfo.DefaultDataCenterInfo.INSTANCE;
        if(DataCenterInfo.Name.Amazon.name().equalsIgnoreCase(proto.getDataCenter().getName().name())) {
            dcInfo = new AmazonInfo();
            Map<String,String> mt = new HashMap<String, String>();
            for(Property property : proto.getDataCenter().getPropertyList()) {
                mt.put(property.getKey(), property.getValue());
            }
            ((AmazonInfo)dcInfo).setMetadata(mt);
        }
       
        builder.setDataCenterInfo(dcInfo);
        
        if(proto.getLease() != null){
            Lease leaseProto = proto.getLease();
            LeaseInfo.Builder leaseBuilder = LeaseInfo.Builder.newBuilder();
            leaseBuilder.setClock(leaseProto.getClock()).
                setDurationInSecs(leaseProto.getEvictionDurationInSecs()).
                setEvictionTimestamp(leaseProto.getEvictionTimestamp()).
                setRegistrationTimestamp(leaseProto.getRegistrationTimestamp()).
                setRenewalIntervalInSecs(leaseProto.getRenewalIntervalInSecs()).
                setRenewalTimestamp(leaseProto.getLastRenewalTimestamp());
            builder.setLeaseInfo(leaseBuilder.build());
        }
        
        for(Property property : proto.getPropertyList()) {
            builder.add(property.getKey(), property.getValue());
        }

        return builder.build();
    }
}