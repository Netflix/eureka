package com.netflix.eureka.transport.utils;


import java.util.concurrent.TimeUnit;

import com.netflix.eureka.transport.MessageBroker;
import rx.Observable;

/**
 * Client/server heartbeat implementation on top of {@link MessageBroker}.
 *
 * @author Tomasz Bak
 */
public class HeartBeatHandler {

    public abstract static class HeartbeatClient<T> {
        public HeartbeatClient(MessageBroker from, long timeout, TimeUnit unit) {
        }

        /**
         * Set on error if client/server communication is broken.
         */
        public Observable<Void> connectionStatus() {
            throw new IllegalStateException("not implemented yet");
        }

        public void shutdown() {
            throw new IllegalStateException("not implemented yet");
        }

        protected abstract T heartbeatMessage();
    }

    public static class HeartbeatServer<T> {
        public HeartbeatServer(MessageBroker to, long timeout, TimeUnit unit) {
        }

        /**
         * Returns heartbeat messages, or sets error if there is a timeout.
         */
        public Observable<T> receivedHeartbeats() {
            throw new IllegalStateException("not implemented yet");
        }

        /**
         * Set on error if client/server communication is broken.
         */
        public Observable<Void> connectionStatus() {
            throw new IllegalStateException("not implemented yet");
        }

        public void shutdown() {
            throw new IllegalStateException("not implemented yet");
        }
    }
}
