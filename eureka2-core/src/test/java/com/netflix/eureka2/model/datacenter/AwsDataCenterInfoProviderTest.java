/*
 * Copyright 2014 Netflix, Inc.
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

package com.netflix.eureka2.model.datacenter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.testkit.data.builder.SampleAwsDataCenterInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

/**
 * @author Tomasz Bak
 */
public class AwsDataCenterInfoProviderTest {

    private static final Pattern URI_RE = Pattern.compile("^http[s]?://[^:]+(:[\\d]*)([^?]*).*");

    private static final Map<String, String> AWS_META_INFO_MAP = new HashMap<>();

    private static final AwsDataCenterInfo DATA_CENTER_INFO = SampleAwsDataCenterInfo.UsEast1aVpc.builder().withPlacementGroup(null).build();

    static {
        AWS_META_INFO_MAP.put("/latest/meta-data/ami-id", DATA_CENTER_INFO.getAmiId());
        AWS_META_INFO_MAP.put("/latest/meta-data/instance-id", DATA_CENTER_INFO.getInstanceId());
        AWS_META_INFO_MAP.put("/latest/meta-data/instance-type", DATA_CENTER_INFO.getInstanceType());
        AWS_META_INFO_MAP.put("/latest/meta-data/local-hostname", DATA_CENTER_INFO.getPrivateAddress().getHostName());
        AWS_META_INFO_MAP.put("/latest/meta-data/local-ipv4", DATA_CENTER_INFO.getPrivateAddress().getIpAddress());
        AWS_META_INFO_MAP.put("/latest/meta-data/public-hostname", DATA_CENTER_INFO.getPublicAddress().getHostName());
        AWS_META_INFO_MAP.put("/latest/meta-data/public-ipv4", DATA_CENTER_INFO.getPublicAddress().getIpAddress());
        AWS_META_INFO_MAP.put("/latest/meta-data/placement/availability-zone", DATA_CENTER_INFO.getZone());
        AWS_META_INFO_MAP.put("/latest/meta-data/placement/availability-zone", DATA_CENTER_INFO.getZone());
        AWS_META_INFO_MAP.put("/latest/meta-data/placement/availability-zone", DATA_CENTER_INFO.getZone());
        AWS_META_INFO_MAP.put("/latest/meta-data/mac", DATA_CENTER_INFO.getEth0mac());
        AWS_META_INFO_MAP.put("/latest/meta-data/network/interfaces/macs/" + DATA_CENTER_INFO.getEth0mac() + "/vpc-id", DATA_CENTER_INFO.getVpcId());
        AWS_META_INFO_MAP.put("/latest/dynamic/instance-identity/document",
                "{\n" +
                        "  \"accountId\" : \"" + DATA_CENTER_INFO.getAccountId() + "\"" +
                        "  \"instanceId\" : \"" + DATA_CENTER_INFO.getInstanceId() + "\"" +
                        "  \"imageId\" : \"" + DATA_CENTER_INFO.getAmiId() + "\"" +
                        "  \"instanceType\" : \"" + DATA_CENTER_INFO.getInstanceType() + "\"" +
                        "  \"kernelId\" : \"someKernalId\",\n" +
                        "  \"ramdiskId\" : null,\n" +
                        "  \"pendingTime\" : \"2015-01-01T00:00:00Z\",\n" +
                        "  \"architecture\" : \"x86_64\",\n" +
                        "  \"region\" : \"" + DATA_CENTER_INFO.getRegion() + "\"" +
                        "  \"version\" : \"2010-01-01\",\n" +
                        "  \"availabilityZone\" : \"" + DATA_CENTER_INFO.getZone() + "\"" +
                        "  \"privateIp\" : \"" + DATA_CENTER_INFO.getPrivateAddress().getIpAddress() + "\"" +
                        "}"
        );
    }

    private ExtTestSubscriber<AwsDataCenterInfo> testSubscriber = new ExtTestSubscriber<>();
    private HttpServer<ByteBuf, ByteBuf> httpServer;
    private volatile boolean sendError;
    private volatile boolean isNotVpc;

    @Before
    public void setUp() throws Exception {
        httpServer = RxNetty.createHttpServer(0, new RequestHandler<ByteBuf, ByteBuf>() {
            @Override
            public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
                if (sendError) {
                    response.setStatus(HttpResponseStatus.INTERNAL_SERVER_ERROR);
                    return response.close();
                }
                Matcher uriMatcher = URI_RE.matcher(request.getPath()); // RxNetty client sends absolute URI, so we need to trim it down
                String path = uriMatcher.matches() ? uriMatcher.group(2) : request.getPath();
                String metaValue = AWS_META_INFO_MAP.get(path);
                if (isNotVpc && metaValue == DATA_CENTER_INFO.getVpcId()) {  // return 404 for not vpc
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                    return response.flush();
                }
                if (metaValue == null) {
                    response.setStatus(HttpResponseStatus.NOT_FOUND);
                    return response.flush();
                }
                return response.writeStringAndFlush(metaValue);
            }
        }).start();
    }

    @After
    public void tearDown() throws Exception {
        if (httpServer != null) {
            httpServer.shutdown();
        }
    }

    @Test(timeout = 60000)
    public void testLoadingOfMetaInfo() throws Exception {
        AwsDataCenterInfoProvider provider = new AwsDataCenterInfoProvider("http://localhost:" + httpServer.getServerPort() + "/latest/");
        provider.dataCenterInfo().single().subscribe(testSubscriber);

        AwsDataCenterInfo resolvedDataCenterInfo = testSubscriber.takeNext(10, TimeUnit.SECONDS);
        assertEquals("Resolved data center info not identical to the reference one", DATA_CENTER_INFO, resolvedDataCenterInfo);
    }

    @Test(timeout = 60000)
    public void testLoadingOfMetaInfoWithSomeNotAvailable() throws Exception {
        isNotVpc = true;
        AwsDataCenterInfoProvider provider = new AwsDataCenterInfoProvider("http://localhost:" + httpServer.getServerPort() + "/latest/");
        provider.dataCenterInfo().single().subscribe(testSubscriber);

        AwsDataCenterInfo resolvedDataCenterInfo = testSubscriber.takeNext(10, TimeUnit.SECONDS);
        DataCenterInfo expected = InstanceModel.getDefaultModel().newAwsDataCenterInfo().withAwsDataCenter(DATA_CENTER_INFO)
                .withVpcId(null).build();
        assertEquals("Resolved data center info not identical to the reference one", expected, resolvedDataCenterInfo);
    }

    @Test(timeout = 60000)
    public void testServerErrorPropagation() throws Exception {
        sendError = true;
        AwsDataCenterInfoProvider provider = new AwsDataCenterInfoProvider("http://localhost:" + httpServer.getServerPort() + "/latest/");

        Notification<AwsDataCenterInfo> notification = provider.dataCenterInfo().materialize().single().toBlocking().toFuture().get(10, TimeUnit.SECONDS);

        assertSame("Expected error notification", Kind.OnError, notification.getKind());
    }

    @Test(timeout = 60000)
    public void testConnectionErrorPropagation() throws Exception {
        AwsDataCenterInfoProvider provider = new AwsDataCenterInfoProvider("http://localhost:0" + "/latest/");

        Notification<AwsDataCenterInfo> notification = provider.dataCenterInfo().materialize().single().toBlocking().toFuture().get(10, TimeUnit.SECONDS);

        assertSame("Expected error notification", Kind.OnError, notification.getKind());
    }
}