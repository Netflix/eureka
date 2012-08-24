package com.netflix.eureka;

import java.util.Arrays;
import java.util.List;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;

public class SampleEurekaClient {
    private volatile static boolean stop = false;
    public static void main(String args[]) {
        Thread t = new Thread(new MyClient());
        t.start();
        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceStatus.UP);
        try {
            Thread.sleep(700000);
        } catch (InterruptedException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        System.out.println(DiscoveryManager.getInstance().getDiscoveryClient()
                .getNextServerFromEureka("sampleserver.netflix.com", false));
        stop = true;
        DiscoveryManager.getInstance().shutdownComponent();

    }

    private static class MyClient implements Runnable {

    

        MyClient() {
            DiscoveryManager.getInstance().initComponent(
                    new MyDataCenterInstanceConfig() {
                        @Override
                        public String getVirtualHostName() {
                            return "sampleserver.netflix.com";
                        }

                        @Override
                        public String getAppname() {
                            return "Karthik";
                        }
                    }, new DefaultEurekaClientConfig() {
                        @Override
                        public boolean shouldUseDnsForFetchingServiceUrls() {
                            return false;
                        }

                        @Override
                        public List<String> getEurekaServerServiceUrls(
                                String myZone) {
                            String[] hosts = { "http://localhost/discovery/v2/" };
                            return Arrays.asList(hosts);

                        }
                    });

        }

        @Override
        public void run() {
        while (!stop) {
                    
                }
            
        }

    }
}
