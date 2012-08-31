/*
 * Copyright 2012 Netflix, Inc.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.netflix.eureka;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Date;

import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.appinfo.MyDataCenterInstanceConfig;
import com.netflix.config.DynamicPropertyFactory;
import com.netflix.discovery.DefaultEurekaClientConfig;
import com.netflix.discovery.DiscoveryManager;

/**
 * Sample Eureka client that discovers the service using Eureka and sends
 * requests.
 * 
 * @author Karthik Ranganathan
 * 
 */
public class SampleEurekaClient {
    private static final DynamicPropertyFactory configInstance = com.netflix.config.DynamicPropertyFactory
            .getInstance();

    public void sendRequestToServiceUsingEureka() {
        // Register with Eureka
        DiscoveryManager.getInstance().initComponent(
                new MyDataCenterInstanceConfig(),
                new DefaultEurekaClientConfig());
        ApplicationInfoManager.getInstance().setInstanceStatus(
                InstanceStatus.UP);
        String vipAddress = configInstance.getStringProperty(
                "eureka.vipAddress", "sampleservice.mydomain.net").get();
        InstanceInfo nextServerInfo = DiscoveryManager.getInstance()
                .getDiscoveryClient()
                .getNextServerFromEureka(vipAddress, false);

        Socket s = new Socket();
        int serverPort = nextServerInfo.getPort();
        try {
            s.connect(new InetSocketAddress(nextServerInfo.getHostName(),
                    serverPort));
        } catch (IOException e) {
            System.err.println("Could not connect to the server :"
                    + nextServerInfo.getHostName() + " at port " + serverPort);
        }
        try {
            System.out.println("Connected to server. Sending a sample request");
            PrintStream out = new PrintStream(s.getOutputStream());
            out.println("Sample request " + new Date());
            String str = null;
            System.out.println("Waiting for server response..");
            BufferedReader rd = new BufferedReader(new InputStreamReader(
                    s.getInputStream()));
            str = rd.readLine();
            if (str != null) {
                System.out
                        .println("Received response from server. Communication all fine using Eureka :");
                System.out.println("Exiting the client. Demo over..");
            }
            rd.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.unRegisterWithEureka();
    }

    public void unRegisterWithEureka() {
        // Un register from eureka.
        DiscoveryManager.getInstance().shutdownComponent();
    }

    public static void main(String args[]) {
        SampleEurekaClient sampleEurekaService = new SampleEurekaClient();
        sampleEurekaService.sendRequestToServiceUsingEureka();

    }

}
