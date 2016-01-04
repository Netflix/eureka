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

package com.netflix.eureka2.spi.channel;

import rx.Observable;

/**
 */
public class ChannelPipeline<I, O> {

    private final String pipelineId;
    private final ChannelHandler<I, O>[] handlers;
    private final ChannelContext<I, O>[] contexts;

    public ChannelPipeline(String pipelineId, ChannelHandler<I, O>... handlers) {
        this.pipelineId = pipelineId;
        this.handlers = handlers;
        this.contexts = initializeContexts();
    }

    public String getPipelineId() {
        return pipelineId;
    }

    public ChannelHandler<I, O> getFirst() {
        return handlers[0];
    }

    public Observable<Void> lifecycle() {
        return null;
    }

    private ChannelContext<I, O>[] initializeContexts() {
        ChannelContext[] contexts = new ChannelContext[handlers.length];
        for (int i = 0; i < handlers.length; i++) {
            contexts[i] = new ChannelContext<>(this, i + 1 < handlers.length ? handlers[i + 1] : null);
        }
        // Do not merge these two loops into one. Channel initialization may require access to next handler
        // in pipeline
        for (int i = 0; i < handlers.length; i++) {
            handlers[i].init(contexts[i]);
        }
        return contexts;
    }
}
