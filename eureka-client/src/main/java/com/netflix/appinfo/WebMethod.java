package com.netflix.appinfo;

import com.netflix.discovery.converters.Auto;
import com.netflix.discovery.provider.Serializer;
import com.thoughtworks.xstream.annotations.XStreamAlias;
import com.thoughtworks.xstream.annotations.XStreamAsAttribute;

@Serializer("com.netflix.discovery.converters.EntityBodyConverter")
@XStreamAlias("method")
public class WebMethod {
    @XStreamAlias("operationName")
    private String operationName;
    @XStreamAlias("action")
    private String action;
    
    public void setOperationName(String operationName) {
        this.operationName = operationName;
    }
    
    public void setAction(String action) {
        this.action = action;
    }
    
    public String getOperationName() {
        return operationName;
    }
    
    public String getAction() {
        return action;
    }
}