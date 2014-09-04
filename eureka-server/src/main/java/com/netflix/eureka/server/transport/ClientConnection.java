package com.netflix.eureka.server.transport;

import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.registry.InstanceInfo;
import rx.Observable;
import rx.Observer;

/**
 * Connection abstraction for clients of eureka servers.
 *
 * Any implementation of this connection must support strict ordering of messages.
 *
 * @author Nitesh Kant
 */
public interface ClientConnection {

    /**
     * Returns the input stream for this transport.
     *
     * @return The input stream for this transport.
     */
    Observable<Object> getInput(); // TODO: O<Object> does not look clean, should we have a base message type?

    /**
     * Sends the passed notification on the associated connection.
     *
     * @param notification Notification to send.
     *
     * @return An {@link Observable} representing the acknowledgment of the notification from the receiver of this
     * notification.
     */
    Observable<Void> sendNotification(ChangeNotification<InstanceInfo> notification);

    /**
     * Shutdown this transport and hence the underlying connection.
     *
     * <b>This method is idempotent.</b>
     */
    void shutdown();

    /**
     * Sends an acknowledgment back to the associated receiver.
     *
     * @return An {@link Observable} representing successful send of the acknowledgment message.
     */
    Observable<Void> sendAcknowledgment();

    /**
     * Sends an error to the associated receiver.
     *
     * @return An {@link Observable} representing successful send of the error.
     */
    Observable<Void> sendError(Throwable error);

    /**
     * Sends completion ({@link Observer#onCompleted()}) of the stream to the associated receiver.
     *
     * The transport does no validation whether the completion was required or not.
     *
     * @return An {@link Observable} representing successful send of the completion.
     */
    Observable<Void> sendOnComplete();
}
