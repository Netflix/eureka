package com.netflix.eureka2.client.functions;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.ChangeNotifications;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Observable;
import rx.Observable.Transformer;
import rx.functions.Func1;

/**
 * @author Tomasz Bak
 */
public final class ChangeNotificationFunctions {

    private ChangeNotificationFunctions() {
    }

    /**
     * Convert change notification stream with buffering sentinels into stream of lists, where each
     * list element contains a batch of data delineated by the markers. Only non-empty lists are
     * issued, which means that for two successive BufferSentinels from the stream, the second
     * one will be swallowed.
     *
     * @return observable of non-empty list objects
     */
    public static <T> Transformer<ChangeNotification<T>, List<ChangeNotification<T>>> buffers() {
        return new Transformer<ChangeNotification<T>, List<ChangeNotification<T>>>() {
            @Override
            public Observable<List<ChangeNotification<T>>> call(Observable<ChangeNotification<T>> notifications) {
                final AtomicReference<List<ChangeNotification<T>>> bufferRef = new AtomicReference<>();
                return notifications.map(new Func1<ChangeNotification<T>, List<ChangeNotification<T>>>() {
                    @Override
                    public List<ChangeNotification<T>> call(ChangeNotification<T> notification) {
                        List<ChangeNotification<T>> buffer = bufferRef.get();
                        if (notification.getKind() == Kind.BufferSentinel) {
                            bufferRef.set(null);
                            return buffer;
                        }
                        if (buffer == null) {
                            bufferRef.set(buffer = new ArrayList<ChangeNotification<T>>());
                        }
                        buffer.add(notification);
                        return null;
                    }
                }).filter(RxFunctions.filterNullValuesFunc());
            }
        };
    }

    /**
     * Collapse observable of change notification batches into a set of currently known items.
     *
     * @return observable of distinct set objects
     */
    public static <T> Transformer<List<ChangeNotification<T>>, Set<T>> snapshots() {
        return ChangeNotifications.snapshots();
    }
}
