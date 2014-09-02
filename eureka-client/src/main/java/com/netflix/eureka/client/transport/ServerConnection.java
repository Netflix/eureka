package com.netflix.eureka.client.transport;

import rx.Observable;

/**
 * Connection abstraction for servers to which a eureka client connects.
 *
 * Any implementation of this connection must support strict ordering of messages.
 *
 * @author Nitesh Kant
 */
public interface ServerConnection {

    /**
     * Returns the input stream for this transport.
     *
     * @return The input stream for this transport.
     */
    Observable<Object> getInput(); // TODO: O<Object> does not look clean, should we have a base message type?

    /**
     * Shutdown this transport and hence the underlying connection.
     *
     * <b>This method is idempotent.</b>
     */
    void shutdown();

    /**
     * Sends a message to the server and expects an acknowledgment from the server.
     *
     * @param message Message to send.
     *
     * @return Acknowledgment sent from the server.
     */
    Observable<Void> send(Object message);

    /**
     * Sends a heartbeat to the server.
     *
     * @return Acknowledgment of sending the heartbeat.
     */
    Observable<Void> sendHeartbeat();

    /**
     * Sends an acknowledgment back to the associated receiver.
     *
     * @return An {@link rx.Observable} representing successful send of the acknowledgment message.
     */
    Observable<Void> sendAcknowledgment();

    /**
     * Sends an error to the associated receiver.
     *
     * @return An {@link rx.Observable} representing successful send of the error.
     */
    Observable<Void> sendError(Throwable error);

    /**
     * Closes this connection.
     */
    void close();
}
