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

package com.netflix.eureka.client.local;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka.client.DataCenterInfoProvider;
import com.netflix.eureka.registry.AwsDataCenterInfo;
import com.netflix.eureka.registry.AwsDataCenterInfo.AwsDataCenterInfoBuilder;
import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.subjects.ReplaySubject;

/**
 * Loads instance information from AWS metadata host. The data is loaded only once
 * on the first access, and its cashed and reused for subsequent {@link #dataCenterInfo()} calls.
 *
 * @author Tomasz Bak
 */
public class AwsDataCenterInfoProvider implements DataCenterInfoProvider {

    private static final String AWS_API_VERSION = "latest";
    private static final String AWS_METADATA_URI = "http://169.254.169.254/" + AWS_API_VERSION + "/meta-data/";
    private final String metaDataURI;

    enum MetaDataKey {
        AmiId("ami-id") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withAmiId(metaInfoValue);
            }
        },
        InstanceId("instance-id") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withInstanceId(metaInfoValue);
            }
        },
        InstanceType("instance-type") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withInstanceType(metaInfoValue);
            }
        },
        PublicHostname("public-hostname") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withPublicHostName(metaInfoValue);
            }
        },
        PublicIpv4("public-ipv4") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withPublicIPv4(metaInfoValue);
            }
        },
        LocalHostName("local-hostname") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withPrivateHostName(metaInfoValue);
            }
        },
        LocalIpv4("local-ipv4") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withPrivateIPv4(metaInfoValue);
            }
        },
        AvailabilityZone("availability-zone", "placement/") {
            @Override
            public AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue) {
                return builder.withZone(metaInfoValue);
            }
        };

        private final String name;
        private final String path;

        MetaDataKey(String name) {
            this(name, "");
        }

        MetaDataKey(String name, String path) {
            this.name = name;
            this.path = path;
        }

        public String getName() {
            return name;
        }

        public String getPath() {
            return path;
        }

        public abstract AwsDataCenterInfoBuilder apply(AwsDataCenterInfoBuilder builder, String metaInfoValue);
    }

    private final AtomicReference<Observable<AwsDataCenterInfo>> dataCenterInfoRef = new AtomicReference<>();

    public AwsDataCenterInfoProvider() {
        this.metaDataURI = AWS_METADATA_URI;
    }

    // For testing purposes.
    AwsDataCenterInfoProvider(String metaDataURI) {
        this.metaDataURI = metaDataURI;
    }

    @Override
    public Observable<AwsDataCenterInfo> dataCenterInfo() {
        if (dataCenterInfoRef.get() != null) {
            return dataCenterInfoRef.get();
        }
        dataCenterInfoRef.compareAndSet(null, readMetaInfo());
        return dataCenterInfoRef.get();
    }

    private Observable<AwsDataCenterInfo> readMetaInfo() {
        final ReplaySubject<AwsDataCenterInfo> subject = ReplaySubject.create();

        final AwsDataCenterInfoBuilder builder = new AwsDataCenterInfoBuilder();
        Observable.from(MetaDataKey.values()).flatMap(new Func1<MetaDataKey, Observable<Void>>() {
            @Override
            public Observable<Void> call(final MetaDataKey key) {
                String uri = metaDataURI + '/' + key.getPath() + key.getName();
                return RxNetty.createHttpGet(uri).flatMap(new Func1<HttpClientResponse<ByteBuf>, Observable<String>>() {
                    @Override
                    public Observable<String> call(HttpClientResponse<ByteBuf> response) {
                        if (response.getStatus().code() / 100 != 2) {
                            return Observable.error(new IOException("Server returned error status " + response.getStatus()));
                        }
                        return response.getContent().map(new Func1<ByteBuf, String>() {
                            @Override
                            public String call(ByteBuf byteBuf) {
                                return byteBuf.toString(Charset.defaultCharset());
                            }
                        });
                    }
                }).map(new Func1<String, Void>() {
                    @Override
                    public Void call(String metaValue) {
                        key.apply(builder, metaValue);
                        return null;
                    }
                });
            }
        }).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                subject.onNext(builder.build());
                subject.onCompleted();
            }

            @Override
            public void onError(Throwable e) {
                subject.onError(e);
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });

        return subject;
    }
}
