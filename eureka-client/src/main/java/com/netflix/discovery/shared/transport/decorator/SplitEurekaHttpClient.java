/*
 * Copyright 2015 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.discovery.shared.transport.decorator;

import com.netflix.appinfo.InstanceInfo;
import com.netflix.appinfo.InstanceInfo.InstanceStatus;
import com.netflix.discovery.shared.Applications;
import com.netflix.discovery.shared.transport.EurekaHttpClient;
import com.netflix.discovery.shared.transport.EurekaHttpResponse;

/**
 * In certain situation it is important to maintain separately connectivity for registration and query operations.
 * For example, in deployment with read-only server the query operations can be redirected from write cluster to the
 * read only one, while registration requests are bound always to the write servers.
 *
 * @author Tomasz Bak
 */
public class SplitEurekaHttpClient implements EurekaHttpClient {

    private final EurekaHttpClient registeringClient;
    private final EurekaHttpClient queryClient;

    public SplitEurekaHttpClient(EurekaHttpClient registeringClient, EurekaHttpClient queryClient) {
        this.registeringClient = registeringClient;
        this.queryClient = queryClient;
    }

    @Override
    public EurekaHttpResponse<Void> register(InstanceInfo info) {
        return registeringClient.register(info);
    }

    @Override
    public EurekaHttpResponse<Void> cancel(String appName, String id) {
        return registeringClient.cancel(appName, id);
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> sendHeartBeat(String appName, String id, InstanceInfo info, InstanceStatus overriddenStatus) {
        return registeringClient.sendHeartBeat(appName, id, info, overriddenStatus);
    }

    @Override
    public EurekaHttpResponse<Void> statusUpdate(String appName, String id, InstanceStatus newStatus, InstanceInfo info) {
        return registeringClient.statusUpdate(appName, id, newStatus, info);
    }

    @Override
    public EurekaHttpResponse<Void> deleteStatusOverride(String appName, String id, InstanceInfo info) {
        return registeringClient.deleteStatusOverride(appName, id, info);
    }

    @Override
    public EurekaHttpResponse<Applications> getApplications() {
        return queryClient.getApplications();
    }

    @Override
    public EurekaHttpResponse<Applications> getDelta() {
        return queryClient.getDelta();
    }

    @Override
    public EurekaHttpResponse<InstanceInfo> getInstance(String appName, String id) {
        return queryClient.getInstance(appName, id);
    }

    @Override
    public void shutdown() {
        registeringClient.shutdown();
        queryClient.shutdown();
    }
}
