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

package com.netflix.eureka2.spi.transport;

import rx.Observable;

import java.util.concurrent.TimeoutException;

/**
 * <p>FIXME Lifecycle concept exists at multiple levels. Can we abstract it away and share the implementation?
 * <p>FIXME What to do with unrecognized messages - dead letter mailbox?
 * <p>
 * Bidirectional communication channel between Eureka client and server.
 *
 * Messages can be send with and without acknowledgement. If acknowledgment is not received within
 * defined timeout period, the corresponding observable is completed with a {@link TimeoutException} error.
 *
 * @author Tomasz Bak
 */
public interface EurekaConnection {

    /**
     * Connection readable name, which should include enough information to identify
     * the other side.
     */
    String name();

    /**
     * Submit a message one-way.
     *
     * @return observable completing normally if acknowledgement was received, or with
     *         exception if message could not be delivered
     */
    Observable<Void> submit(Object message);

    /**
     * Submit a message and expect acknowledgement in return. Acknowledgements do not
     * provide any insight into processing status on the other side (success or failure).
     *
     * @return observable completing normally if acknowledgement was received, or with
     *         exception if message could not be delivered or acknowledgement timeout happened
     */
    Observable<Void> submitWithAck(Object message);

    /**
     * Submit a message with a user content and expect acknowledgement in return. Acknowledgement does not
     * provide any insight into processing status on the other side (success or failure). If the
     * acknowledgement is not received in the specified amount of time, the return observable returns an error.
     *
     * @param  timeout maximum waiting time for acknowledgement
     *
     * @return observable that returns exactly one {@link com.netflix.eureka2.spi.protocol.Acknowledgement} object or {@link TimeoutException}
     *         if no acknowledgment received on time
     */
    Observable<Void> submitWithAck(Object message, long timeout);

    /**
     * Send back an acknowledgement. Acknowledgement request is ignored if a timeout expired.
     *
     * @return observable completing normally if acknowledgement was successfully submitted,
     *         or with exception if message could not be delivered
     */
    Observable<Void> acknowledge();

    /**
     * @return observable of messages send by the other side of a connection
     */
    Observable<Object> incoming();

    /**
     * FIXME: why would we need this method?
     */
    Observable<Void> onError(Throwable error);

    /**
     * FIXME: why would we need this method?
     */
    Observable<Void> onCompleted();

    /**
     * Close a connection.
     */
    void shutdown();

    /**
     * Close a connection in error.
     */
    void shutdown(Throwable e);

    /**
     * Message broker status observable.
     */
    Observable<Void> lifecycleObservable();
}
