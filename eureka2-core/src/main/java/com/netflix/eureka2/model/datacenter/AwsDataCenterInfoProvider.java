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

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.client.HttpClientResponse;
import rx.Observable;
import rx.Subscriber;
import rx.functions.Func1;
import rx.subjects.AsyncSubject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads instance information from AWS metadata host. The data is loaded only once
 * on the first access, and its cashed and reused for subsequent {@link #dataCenterInfo()} calls.
 *
 * @author Tomasz Bak
 */
public class AwsDataCenterInfoProvider implements DataCenterInfoProvider {

    private static final String AWS_API_VERSION = "latest/";
    private static final String AWS_METADATA_URI = "http://169.254.169.254/" + AWS_API_VERSION;
    private static final String INSTANCE_DATA = "meta-data/";
    private static final String DYNAMIC_DATA = "dynamic/";
    private final String metaDataURI;

    enum MetaDataKey {
        AmiId("ami-id") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withAmiId(metaInfoValue);
            }
        },
        InstanceId("instance-id") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withInstanceId(metaInfoValue);
            }
        },
        InstanceType("instance-type") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withInstanceType(metaInfoValue);
            }
        },
        PublicHostname("public-hostname") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withPublicHostName(metaInfoValue);
            }
        },
        PublicIpv4("public-ipv4") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withPublicIPv4(metaInfoValue);
            }
        },
        LocalHostName("local-hostname") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withPrivateHostName(metaInfoValue);
            }
        },
        LocalIpv4("local-ipv4") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withPrivateIPv4(metaInfoValue);
            }
        },
        AvailabilityZone("availability-zone", "placement/") {
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withZone(metaInfoValue);
            }
        },
        Mac("mac") {  // mac (for eth0) is needed for vpcId
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withEth0mac(metaInfoValue);
            }
        },
        VpcId("vpc-id", "network/interfaces/macs/") {
            @Override
            public String finalUri(String metaDataUri, String metaDataType, String... args) {
                String eth0mac = args.length == 0 ? "" : args[0];
                return metaDataUri + metaDataType + getPath() + eth0mac + "/" + getName();
            }
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                return builder.withVpcId(metaInfoValue);
            }
        },
        AccountId("document", "instance-identity/") {
            private Pattern pattern = Pattern.compile("\"accountId\"\\s?:\\s?\\\"([A-Za-z0-9]*)\\\"");
            @Override
            protected AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
                // no need to use a json deserializer, do a custom regex parse
                Matcher matcher = pattern.matcher(metaInfoValue);
                if (matcher.find()) {
                    return builder.withAccountId(matcher.group(1));
                } else {
                    return builder.withAccountId(null);
                }
            }
        };


        private final String name;
        private final String path;
        private String value;

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

        public String getValue() {
            return value;
        }

        public String finalUri(String metaDataUri, String metaDataType, String... args) {
            return metaDataUri + metaDataType + path + name;
        }

        public AwsDataCenterInfo.Builder apply(AwsDataCenterInfo.Builder builder, String metaInfoValue) {
            this.value = metaInfoValue;
            return doApply(builder, metaInfoValue);
        }

        protected abstract AwsDataCenterInfo.Builder doApply(AwsDataCenterInfo.Builder builder, String metaInfoValue);
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
        final AsyncSubject<AwsDataCenterInfo> subject = AsyncSubject.create();

        final AwsDataCenterInfo.Builder builder = new AwsDataCenterInfo.Builder();
        Observable.from(MetaDataKey.values())
                .flatMap(new Func1<MetaDataKey, Observable<MetaDataKey>>() {
                    @Override
                    public Observable<MetaDataKey> call(final MetaDataKey key) {
                        String uri;
                        switch (key) {
                            case VpcId:  // ignore vpcId for now, it needs mac to be loaded first
                                return Observable.empty();
                            case AccountId:
                                uri = key.finalUri(metaDataURI, DYNAMIC_DATA);
                                break;
                            default:
                                uri = key.finalUri(metaDataURI, INSTANCE_DATA);
                                break;
                        }
                        return RxNetty.createHttpGet(uri)
                                .flatMap(EC2_METADATA_RESPONSE_FUNC)
                                .map(new Func1<String, MetaDataKey>() {
                                    @Override
                                    public MetaDataKey call(String metaValue) {
                                        key.apply(builder, metaValue);
                                        return key;
                                    }
                                });
                    }
                })
                .filter(new Func1<MetaDataKey, Boolean>() {
                    @Override
                    public Boolean call(MetaDataKey metaDataKey) {
                        return metaDataKey == MetaDataKey.Mac;
                    }
                })
                .flatMap(new Func1<MetaDataKey, Observable<Void>>() {
                    @Override
                    public Observable<Void> call(MetaDataKey macKey) {
                        String vpcUri = MetaDataKey.VpcId.finalUri(metaDataURI, INSTANCE_DATA, macKey.getValue());
                        return RxNetty.createHttpGet(vpcUri)
                                .flatMap(EC2_METADATA_RESPONSE_FUNC)
                                .map(new Func1<String, Void>() {
                                    @Override
                                    public Void call(String metaValue) {
                                        MetaDataKey.VpcId.apply(builder, metaValue);
                                        return null;
                                    }
                                });
                    }
                })
                .subscribe(new Subscriber<Void>() {
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


    private static final Func1<HttpClientResponse<ByteBuf>, Observable<String>> EC2_METADATA_RESPONSE_FUNC =
            new Func1<HttpClientResponse<ByteBuf>, Observable<String>>() {
                @Override
                public Observable<String> call(HttpClientResponse<ByteBuf> response) {
                    if (response == null) {
                        return Observable.error(new IOException("Server returned null response"));
                    }

                    int responseCode = response.getStatus().code();
                    if (responseCode >= 200 && responseCode <= 299) {
                        return response.getContent().map(new Func1<ByteBuf, String>() {
                            @Override
                            public String call(ByteBuf byteBuf) {
                                return byteBuf.toString(Charset.defaultCharset());
                            }
                        });
                    } else if (responseCode == 404) {
                        return Observable.empty();
                    } else {
                        return Observable.error(new IOException("Server returned error status " + response.getStatus()));
                    }
                }
            };
}
