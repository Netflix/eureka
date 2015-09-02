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

package com.netflix.eureka2.server.http.proxy;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import rx.Observable;

/**
 * @author Tomasz Bak
 */
public class HttpRequestDispatcher implements RequestHandler<ByteBuf, ByteBuf> {

    private final List<String> pathPrefixes = new CopyOnWriteArrayList<>();
    private final List<RequestHandler<ByteBuf, ByteBuf>> handlers = new CopyOnWriteArrayList<>();

    public void addHandler(String pathPrefix, RequestHandler<ByteBuf, ByteBuf> handler) {
        pathPrefixes.add(pathPrefix);
        handlers.add(handler);
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        String path = request.getPath();
        for (int i = 0; i < pathPrefixes.size(); i++) {
            if (path.startsWith(pathPrefixes.get(i))) {
                return handlers.get(i).handle(request, response);
            }
        }
        response.setStatus(HttpResponseStatus.NOT_FOUND);
        return Observable.empty();
    }
}
