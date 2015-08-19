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

package com.netflix.eureka2.server.rest.diagnostic;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.core.MediaType;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.codec.EurekaCodec;
import com.netflix.eureka2.codec.EurekaCodecs;
import com.netflix.eureka2.codec.RxNettyCodecUtils;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaders.Names;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;
import rx.functions.Action1;
import rx.functions.Func1;

/**
 * {@link MultiSourcedDataHolder} query resource. The following query options are provided for data filtering:
 * <ul>
 *     <li>sortBy - sorting rules</li>
 *     <li>id - id pattern</li>
 *     <li>app - application name pattern</li>
 *     <li>source - source name pattern (matched against any of the name components)</li>
 *     <li>cardinality - number of copy instances</li>
 * </ul>
 *
 * @author Tomasz Bak
 */
@Singleton
public class DiagnosticInstanceHoldersResource implements RequestHandler<ByteBuf, ByteBuf> {

    public static final String PATH_DIAGNOSTIC_ENTRYHOLDERS = "/api/diagnostic/registry/entryholders";

    private static final Pattern INSTANCE_HOLDERS_RE = Pattern.compile(PATH_DIAGNOSTIC_ENTRYHOLDERS);
    private static final Pattern INSTANCE_HOLDER_RE = Pattern.compile(PATH_DIAGNOSTIC_ENTRYHOLDERS + "/([^/]+)$");

    private final EurekaCodec<MultiSourcedDataHolder<InstanceInfo>> fullCodec = EurekaCodecs.getMultiSourcedDataHolderCodec(CodecType.Json);
    private final EurekaCodec<MultiSourcedDataHolder<InstanceInfo>> compactCodec = EurekaCodecs.getCompactMultiSourcedDataHolderCodec(CodecType.Json);
    private final SourcedEurekaRegistry<InstanceInfo> registry;

    @Inject
    public DiagnosticInstanceHoldersResource(SourcedEurekaRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        if (request.getHttpMethod() != HttpMethod.GET) {
            response.setStatus(HttpResponseStatus.METHOD_NOT_ALLOWED);
            return Observable.empty();
        }
        String path = request.getPath();
        Matcher matcher = INSTANCE_HOLDERS_RE.matcher(path);
        if (matcher.matches()) {
            return handleInstanceHoldersRequest(request, response);
        }
        matcher = INSTANCE_HOLDER_RE.matcher(path);
        if (matcher.matches()) {
            return handleInstanceHolderRequest(matcher.group(1), request, response);
        }
        return null;
    }

    private Observable<Void> handleInstanceHoldersRequest(HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
        response.setStatus(HttpResponseStatus.OK);
        response.getHeaders().add(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        final AtomicBoolean first = new AtomicBoolean();
        response.writeString("[");
        registry.getHolders()
                .filter(buildQueryFilter(request))
                .doOnNext(new Action1<MultiSourcedDataHolder<InstanceInfo>>() {
                    @Override
                    public void call(MultiSourcedDataHolder<InstanceInfo> holder) {
                        if (!first.compareAndSet(false, true)) {
                            response.writeString(",");
                        }
                        RxNettyCodecUtils.writeValue(compactCodec, holder, response);
                    }
                }).toBlocking().lastOrDefault(null);
        response.writeString("]");
        return response.flush();
    }

    private Func1<? super MultiSourcedDataHolder<InstanceInfo>, Boolean> buildQueryFilter(HttpServerRequest<ByteBuf> request) {
        // Query parameters
        String id = request.getQueryParameters().get("id") == null ? null : request.getQueryParameters().get("id").get(0);
        final Pattern idPattern = id == null ? null : Pattern.compile(id);

        String app = request.getQueryParameters().get("app") == null ? null : request.getQueryParameters().get("app").get(0);
        final Pattern appPattern = app == null ? null : Pattern.compile(app);

        String source = request.getQueryParameters().get("source") == null ? null : request.getQueryParameters().get("source").get(0);
        final Pattern sourcePattern = source == null ? null : Pattern.compile(source);

        String cardinalityValue = request.getQueryParameters().get("cardinality") == null ? null : request.getQueryParameters().get("cardinality").get(0);
        final int cardinality = cardinalityValue == null ? -1 : Integer.parseInt(cardinalityValue);

        // Always true function if no parameters provided
        if (id == null && app == null && source == null && cardinality <= 0) {
            return new Func1<MultiSourcedDataHolder<InstanceInfo>, Boolean>() {
                @Override
                public Boolean call(MultiSourcedDataHolder<InstanceInfo> holder) {
                    return true;
                }
            };
        }

        // Filter according to the given rules
        return new Func1<MultiSourcedDataHolder<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(MultiSourcedDataHolder<InstanceInfo> holder) {
                if (cardinality > 0 && holder.getAllSources().size() != cardinality) {
                    return false;
                }
                boolean idMatches = idPattern == null || idPattern.matcher(holder.getId()).find();
                if (!idMatches) {
                    return false;
                }
                boolean appMatches = appPattern == null || appPattern.matcher(holder.get().getApp()).find();
                if (!appMatches) {
                    return false;
                }
                if (sourcePattern != null) {
                    boolean matches = false;
                    for (Source s : holder.getAllSources()) {
                        if (s.getOrigin() != null && sourcePattern.matcher(s.getOrigin().name()).find()) {
                            matches = true;
                            break;
                        }
                        if (s.getName() != null && sourcePattern.matcher(s.getName()).find()) {
                            matches = true;
                            break;
                        }
                    }
                    if (!matches) {
                        return false;
                    }
                }
                return true;
            }
        };
    }

    private Observable<Void> handleInstanceHolderRequest(final String id, HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        MultiSourcedDataHolder<InstanceInfo> holder = registry.getHolders().first(new Func1<MultiSourcedDataHolder<InstanceInfo>, Boolean>() {
            @Override
            public Boolean call(MultiSourcedDataHolder<InstanceInfo> holder) {
                return holder.getId().equals(id);
            }
        }).toBlocking().firstOrDefault(null);
        if (holder == null) {
            response.setStatus(HttpResponseStatus.NOT_FOUND);
            return Observable.empty();
        }
        response.setStatus(HttpResponseStatus.OK);
        response.getHeaders().add(Names.CONTENT_TYPE, MediaType.APPLICATION_JSON);
        RxNettyCodecUtils.writeValue(fullCodec, holder, response);
        return response.flush();
    }
}