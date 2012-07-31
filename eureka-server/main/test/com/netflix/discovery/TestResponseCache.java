package com.netflix.discovery;

import java.util.Properties;

import org.junit.Test;

import com.netflix.discovery.resources.ResponseCache;
import com.netflix.discovery.resources.ResponseCache.Key;
import com.netflix.discovery.resources.ResponseCache.KeyType;
import com.netflix.library.NFLibraryManager;
import com.netflix.platform.core.PlatformManager;

public class TestResponseCache {
       @Test
       public void basicTest() throws Throwable{
            try{
                
                Properties props = new Properties();
                props.put("netflix.appinfo.name", "illegalAppName");
                props.put("netflix.appinfo.sid", "120");
                props.put("netflix.appinfo.version", "prod-424566");
                props.put("netflix.appinfo.port", "7001");
                props.put("netflix.appinfo.metadata.name1", "value1");
                props.put("netflix.appinfo.metadata.parent.name1", "parent.value1");
                props.put("netflix.appinfo.metadata.name2", "value2");
                props.setProperty("platform.ListOfComponentsToInit", "LOGGING,APPINFO" );
                
    
                NFLibraryManager.initLibrary(PlatformManager.class,
                        props,
                        true,
                        false); 
                System.out.println( ResponseCache.getInstance().get(new Key(ResponseCache.ALL_APPS,KeyType.XML,Version.V2)));

                System.out.println( ResponseCache.getInstance().get(new Key("CRAP",KeyType.XML,Version.V2)));

            } catch (Exception e) {
                e.printStackTrace();
            }  
        }

  
}
