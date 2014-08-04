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

package com.netflix.eureka.transport;

import java.util.concurrent.TimeoutException;

import io.reactivex.netty.channel.ObservableConnection;
import rx.Observable;

/**
 * One to one bidirectional communication endpoint. Equivalent to {@link ObservableConnection}
 * in RxNetty, with higher level message passing semantics.
 *
 * Submit with acknowledgement:
 * 1. generate correlaction id, and re-package user object with {@link UserContentWithAck}
 * 2. encode message with avro codec (configuered with protocol that knows our data model)
 * 3. create subscription for this message and return to the user
 *
 * Acknowledge a message:
 * 1. Extract correlaction id, and create Acknowledgment object
 * 2. encode message with avro codec
 *
 * Receive a message:
 * 1. Decode object using avro codec
 * 2a) If received UserContent pass to subscriber directly
 * 2b) If received UserContentWithAck, register internally, and pass to subscriber
 * 2c) If received Acknowledgement, forward to the corresponding observable
 *
 * Handling timeouts on sender side:
 * If acknowledgment is not received within timeout period, cancel subscriptions and remove observable
 *
 * Handling timeouts on receiver side:
 * Ignore {@link #acknowledge(UserContentWithAck)} if timeout happened.
 *
 * @author Tomasz Bak
 */
public interface MessageBroker {

    /**
     * Submit a message with a user content one-way.
     */
    void submit(UserContent message);

    /**
     * Submit a message with a user content and expect acknowledgement in return. Acknowledgement does not
     * provide any insight into processing status on the other side (success or failure).
     *
     * @return observable that returns exactly one {@link Acknowledgement} object.
     */
    Observable<Acknowledgement> submitWithAck(UserContent message);

    /**
     * Submit a message with a user content and expect acknowledgement in return. Acknowledgement does not
     * provide any insight into processing status on the other side (success or failure). If the
     * acknowledgement is not received in the specified amount of time, the return observable returns an error.
     *
     * @param  timeout maximum waiting time for acknowledgement
     *
     * @return observable that returns exactly one {@link Acknowledgement} object or {@link TimeoutException}
     *         if no acknowledgment received on time
     */
    Observable<Acknowledgement> submitWithAck(UserContent message, long timeout);

    /**
     * For a received message, send back an acknowledgement. {@link UserContentWithAck} contains a correlation id
     * that is used on the sender side to find an original client request, and {@code Observable<Acknowledgement>}
     * instance. Acknowledgement request is ignored if a timeout expired.
     *
     * @return if acknowledgement was successfuly submitted
     */
    boolean acknowledge(UserContentWithAck message);

    /**
     * @return observable of messages send by the other side of a connection
     */
    Observable<Message> incoming();

    /**
     * Close a connection.
     */
    void shutdown();

    /**
     * Message broker status observable.
     */
    Observable<Void> lifecycleObservable();
}
