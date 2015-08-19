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

package com.netflix.eureka2.server.rest.system;

import javax.inject.Inject;
import javax.ws.rs.core.MediaType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.server.service.EurekaWriteServerSelfInfoResolver;
import com.netflix.eureka2.utils.Json;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import rx.functions.Func1;

import static com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver.META_EUREKA_SERVER_TYPE;
import static com.netflix.eureka2.server.service.selfinfo.SelfInfoResolver.META_EUREKA_WRITE_CLUSTER_ID;

/**
 * Eureka2 write/read cluster setup. The data is based on the registry content, so it presents given
 * node's perspective on the cluster topology.
 *
 * @author Tomasz Bak
 */
public class ClusterTopologyResource implements RequestHandler<ByteBuf, ByteBuf> {

    public static final String PATH_CLUSTER_TOPOLOGY = "/api/system/cluster";

    private static final Pattern CLUSTER_TOPOLOGY_RE = Pattern.compile(PATH_CLUSTER_TOPOLOGY);

    private final EurekaWriteServerSelfInfoResolver selfInfoResolver;
    private final SourcedEurekaRegistry<InstanceInfo> registry;

    @Inject
    public ClusterTopologyResource(EurekaWriteServerSelfInfoResolver selfInfoResolver, SourcedEurekaRegistry registry) {
        this.selfInfoResolver = selfInfoResolver;
        this.registry = registry;
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        if (request.getHttpMethod() != HttpMethod.GET) {
            response.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return Observable.empty();
        }
        String path = request.getPath();
        Matcher matcher = CLUSTER_TOPOLOGY_RE.matcher(path);
        if (matcher.matches()) {
            return handleClusterTopologyRequest(request, response);
        }
        response.setStatus(HttpResponseStatus.NOT_FOUND);
        return Observable.empty();
    }

    private Observable<Void> handleClusterTopologyRequest(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        response.setStatus(HttpResponseStatus.OK);
        response.getHeaders().add(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON);

        return selfInfoResolver.resolve().take(1).flatMap(new Func1<InstanceInfo, Observable<Void>>() {
            @Override
            public Observable<Void> call(final InstanceInfo localInstanceInfo) {
                String clusterId = localInstanceInfo.getMetaData() == null ?
                        null : localInstanceInfo.getMetaData().get(META_EUREKA_WRITE_CLUSTER_ID);
                if (clusterId == null) {
                    clusterId = localInstanceInfo.getVipAddress();
                }
                final String finalClusterId = clusterId;
                List<InstanceInfo> instanceInfos = registry
                        .forSnapshot(Interests.forFullRegistry())
                        .filter(new Func1<InstanceInfo, Boolean>() {
                            @Override
                            public Boolean call(InstanceInfo checkedItem) {
                                if (equalValues(localInstanceInfo.getVipAddress(), checkedItem.getVipAddress())) {
                                    return true;
                                }
                                Map<String, String> meta = checkedItem.getMetaData();
                                if (meta != null) {
                                    if (equalValues(finalClusterId, meta.get(META_EUREKA_WRITE_CLUSTER_ID))) {
                                        return true;
                                    }
                                    // We should require cluster id to be present, but until it is set by Eureka read
                                    // server, this relaxed condition must suffice.
                                    if (meta.containsKey(META_EUREKA_SERVER_TYPE)) {
                                        return true;
                                    }
                                }
                                return false;
                            }
                        }).toList().toBlocking().first();


                List<InstanceInfo> writeNodes = new ArrayList<>();
                List<InstanceInfo> readNodes = new ArrayList<>();
                for (InstanceInfo checkedItem : instanceInfos) {
                    if (equalValues(localInstanceInfo.getVipAddress(), checkedItem.getVipAddress())) {
                        writeNodes.add(checkedItem);
                    } else {
                        readNodes.add(checkedItem);
                    }
                }
                DeploymentDescriptor descriptor = new DeploymentDescriptor(
                        new ClusterDescriptor(finalClusterId, writeNodes),
                        new ClusterDescriptor(finalClusterId, readNodes)
                );
                return response.writeStringAndFlush(Json.toStringJson(descriptor));
            }
        }).timeout(5, TimeUnit.SECONDS);
    }

    private static boolean equalValues(String first, String second) {
        if (first == null || second == null) {
            return false;
        }
        return first.equals(second);
    }
}