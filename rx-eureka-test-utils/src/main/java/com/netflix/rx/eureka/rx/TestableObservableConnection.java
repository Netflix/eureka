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

package com.netflix.rx.eureka.rx;

import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.UnpooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.reactivex.netty.channel.ContentTransformer;
import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;
import rx.Observer;
import rx.subjects.PublishSubject;

/**
 * This class allow creation of {@link ObservableConnection} which input/output channel can
 * be controlled directly by a client (see {@link #testableChannelRead()} and {@link #testableChannelWrite()} methods).
 *
 * @author Tomasz Bak
 */
public class TestableObservableConnection<I, O> extends ObservableConnection<I, O> {

    private final PublishSubject<I> connectionInputSubject = PublishSubject.create();

    private final PublishSubject<O> connectionOutputSubject = PublishSubject.create();

    private volatile boolean isClosed;

    public TestableObservableConnection() {
        super(new EmbeddedChannel(), null, null);
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
}
