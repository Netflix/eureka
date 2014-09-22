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

package com.netflix.eureka.rx;

import java.net.SocketAddress;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.Attribute;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.EventExecutor;
import io.netty.util.concurrent.EventExecutorGroup;
import io.reactivex.netty.channel.ChannelMetricEventProvider;
import io.reactivex.netty.channel.ContentTransformer;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

/**
 * This class allow creation of {@link ObservableConnection} which input/output channel can
 * be contolled directly by a client (see {@link #testableChannelRead()} and {@link #testableChannelWrite()} methods).
 *
 * @author Tomasz Bak
 */
public class TestableObservableConnection<I, O> extends ObservableConnection<I, O> {

    private final PublishSubject<I> connectionInputSubject = PublishSubject.create();

    private final PublishSubject<O> connectionOutputSubject = PublishSubject.create();

    private volatile boolean isClosed;

    public TestableObservableConnection() {
        super(new ChannelHandlerContextStub(), (ChannelMetricEventProvider)null, null);
    }

    /*
     * Test methods.
     */

    public Observer<I> testableChannelRead() {
        return connectionInputSubject;
    }

    public Observable<O> testableChannelWrite() {
        return connectionOutputSubject;
    }

    /*
     * Mocked ObservableConnection public methods.
     */

    @Override
    public Observable<I> getInput() {
        checkIfClosed();
        return connectionInputSubject;
    }

    @Override
    public Observable<Void> close() {
        isClosed = true;
        return Observable.empty();
    }

    @Override
    public Observable<Void> writeAndFlush(O msg) {
        checkIfClosed();
        connectionOutputSubject.onNext(msg);
        return Observable.empty();
    }

    @Override
    public <R> Observable<Void> writeAndFlush(R msg, ContentTransformer<R> transformer) {
        throw new RuntimeException("content transformers not supported on TestableObservableConnection");
    }

    @Override
    public void write(O msg) {
        writeAndFlush(msg);
    }

    @Override
    public <R> void write(R msg, ContentTransformer<R> transformer) {
        throw new RuntimeException("content transformers not supported on TestableObservableConnection");
    }

    @Override
    public void writeBytes(byte[] msg) {
        throw new RuntimeException("writeBytes content not supported on TestableObservableConnection");
    }

    @Override
    public void writeString(String msg) {
        throw new RuntimeException("writeString content not supported on TestableObservableConnection");
    }

    @Override
    public Observable<Void> writeBytesAndFlush(byte[] msg) {
        throw new RuntimeException("writeBytes content not supported on TestableObservableConnection");
    }

    @Override
    public Observable<Void> writeStringAndFlush(String msg) {
        throw new RuntimeException("writeString content not supported on TestableObservableConnection");
    }

    @Override
    public Observable<Void> flush() {
        checkIfClosed();
        return Observable.empty();
    }

    @Override
    public void cancelPendingWrites(boolean mayInterruptIfRunning) {
    }

    @Override
    public ByteBufAllocator getAllocator() {
        return UnpooledByteBufAllocator.DEFAULT;
    }

    @Override
    public ChannelHandlerContext getChannelHandlerContext() {
        throw new RuntimeException("getChannelHandlerContext content not supported on TestableObservableConnection");
    }

    @Override
    public boolean isCloseIssued() {
        return isClosed;
    }

    @Override
    public Observable<Void> close(boolean flush) {
        isClosed = true;
        return Observable.empty();
    }

    private void checkIfClosed() {
        if (isClosed) {
            throw new IllegalStateException("ObservableConnection is already closed");
        }
    }

    /**
     * Stub implementation of {@link ChannelHandlerContext} requried by constructor of {@link ObservableConnection}.
     */
    private static class ChannelHandlerContextStub implements ChannelHandlerContext {
        @Override
        public Channel channel() {
            return null;
        }

        @Override
        public EventExecutor executor() {
            return null;
        }

        @Override
        public String name() {
            return null;
        }

        @Override
        public ChannelHandler handler() {
            return null;
        }

        @Override
        public boolean isRemoved() {
            return false;
        }

        @Override
        public ChannelHandlerContext fireChannelRegistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelUnregistered() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelActive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelInactive() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireExceptionCaught(Throwable cause) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireUserEventTriggered(Object event) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelRead(Object msg) {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelReadComplete() {
            return null;
        }

        @Override
        public ChannelHandlerContext fireChannelWritabilityChanged() {
            return null;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
            return null;
        }

        @Override
        public ChannelFuture disconnect() {
            return null;
        }

        @Override
        public ChannelFuture close() {
            return null;
        }

        @Override
        public ChannelFuture deregister() {
            return null;
        }

        @Override
        public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture disconnect(ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture close(ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture deregister(ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelHandlerContext read() {
            return null;
        }

        @Override
        public ChannelFuture write(Object msg) {
            return null;
        }

        @Override
        public ChannelFuture write(Object msg, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelHandlerContext flush() {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
            return null;
        }

        @Override
        public ChannelFuture writeAndFlush(Object msg) {
            return null;
        }

        @Override
        public ChannelPipeline pipeline() {
            return new ChannelPipelineStub(this);
        }

        @Override
        public ByteBufAllocator alloc() {
            return null;
        }

        @Override
        public ChannelPromise newPromise() {
            return new DefaultChannelPromise(null);
        }

        @Override
        public ChannelProgressivePromise newProgressivePromise() {
            return null;
        }

        @Override
        public ChannelFuture newSucceededFuture() {
            return null;
        }

        @Override
        public ChannelFuture newFailedFuture(Throwable cause) {
            return null;
        }

        @Override
        public ChannelPromise voidPromise() {
            return null;
        }

        @Override
        public <T> Attribute<T> attr(AttributeKey<T> key) {
            return null;
        }

        private static class ChannelPipelineStub implements ChannelPipeline {
            private ChannelHandlerContext stubContext;

            private ChannelPipelineStub(ChannelHandlerContext stubContext) {
                this.stubContext = stubContext;
            }

            @Override
            public ChannelPipeline addFirst(String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addLast(String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelPipeline addFirst(ChannelHandler... handlers) {
                return null;
            }

            @Override
            public ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers) {
                return null;
            }

            @Override
            public ChannelPipeline addLast(ChannelHandler... handlers) {
                return null;
            }

            @Override
            public ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers) {
                return null;
            }

            @Override
            public ChannelPipeline remove(ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelHandler remove(String name) {
                return null;
            }

            @Override
            public <T extends ChannelHandler> T remove(Class<T> handlerType) {
                return null;
            }

            @Override
            public ChannelHandler removeFirst() {
                return null;
            }

            @Override
            public ChannelHandler removeLast() {
                return null;
            }

            @Override
            public ChannelPipeline replace(ChannelHandler oldHandler, String newName, ChannelHandler newHandler) {
                return null;
            }

            @Override
            public ChannelHandler replace(String oldName, String newName, ChannelHandler newHandler) {
                return null;
            }

            @Override
            public <T extends ChannelHandler> T replace(Class<T> oldHandlerType, String newName, ChannelHandler newHandler) {
                return null;
            }

            @Override
            public ChannelHandler first() {
                return null;
            }

            @Override
            public ChannelHandlerContext firstContext() {
                return stubContext;
            }

            @Override
            public ChannelHandler last() {
                return null;
            }

            @Override
            public ChannelHandlerContext lastContext() {
                return null;
            }

            @Override
            public ChannelHandler get(String name) {
                return null;
            }

            @Override
            public <T extends ChannelHandler> T get(Class<T> handlerType) {
                return null;
            }

            @Override
            public ChannelHandlerContext context(ChannelHandler handler) {
                return null;
            }

            @Override
            public ChannelHandlerContext context(String name) {
                return null;
            }

            @Override
            public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
                return null;
            }

            @Override
            public Channel channel() {
                return null;
            }

            @Override
            public List<String> names() {
                return null;
            }

            @Override
            public Map<String, ChannelHandler> toMap() {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelRegistered() {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelUnregistered() {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelActive() {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelInactive() {
                return null;
            }

            @Override
            public ChannelPipeline fireExceptionCaught(Throwable cause) {
                return null;
            }

            @Override
            public ChannelPipeline fireUserEventTriggered(Object event) {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelRead(Object msg) {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelReadComplete() {
                return null;
            }

            @Override
            public ChannelPipeline fireChannelWritabilityChanged() {
                return null;
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress) {
                return null;
            }

            @Override
            public ChannelFuture disconnect() {
                return null;
            }

            @Override
            public ChannelFuture close() {
                return null;
            }

            @Override
            public ChannelFuture deregister() {
                return null;
            }

            @Override
            public ChannelFuture bind(SocketAddress localAddress, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture connect(SocketAddress remoteAddress, SocketAddress localAddress, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture disconnect(ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture close(ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture deregister(ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelPipeline read() {
                return null;
            }

            @Override
            public ChannelFuture write(Object msg) {
                return null;
            }

            @Override
            public ChannelFuture write(Object msg, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelPipeline flush() {
                return null;
            }

            @Override
            public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
                return null;
            }

            @Override
            public ChannelFuture writeAndFlush(Object msg) {
                return null;
            }

            @Override
            public Iterator<Entry<String, ChannelHandler>> iterator() {
                return null;
            }
        }
    }
}
