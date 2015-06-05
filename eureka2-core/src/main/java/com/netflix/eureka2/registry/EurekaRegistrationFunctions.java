package com.netflix.eureka2.registry;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;
import rx.functions.Func1;

/**
 * A collection of functions operating on registration streams.
 *
 * @author Tomasz Bak
 */
public final class EurekaRegistrationFunctions {

    private EurekaRegistrationFunctions() {
    }

    /**
     * Convert registration observable of {@link InstanceInfo} objects, into change notification stream, with
     * a sequence of Kind.Add updates, followed by Kind.Delete when stream onCompletes or onErrors.
     * Errors are not propagated in the returned stream.
     */
    public static Observable<ChangeNotification<InstanceInfoWithSource>> toSourcedChangeNotificationStream(Observable<InstanceInfo> registrationUpdates, final String id, final Source source) {
        return registrationUpdates
                .materialize()
                .map(new Func1<Notification<InstanceInfo>, ChangeNotification<InstanceInfoWithSource>>() {
                    @Override
                    public ChangeNotification<InstanceInfoWithSource> call(Notification<InstanceInfo> notification) {
                        if (notification.getKind() == Kind.OnNext) {
                            return new ChangeNotification<InstanceInfoWithSource>(ChangeNotification.Kind.Add, new InstanceInfoWithSource(notification.getValue(), source));
                        }
                        InstanceInfo toDelete = new InstanceInfo.Builder().withId(id).build();
                        return new ChangeNotification<InstanceInfoWithSource>(ChangeNotification.Kind.Delete, new InstanceInfoWithSource(toDelete, source));
                    }
                });
    }

    public static class InstanceInfoWithSource {
        private final InstanceInfo instanceInfo;
        private final Source source;

        InstanceInfoWithSource(InstanceInfo instanceInfo, Source source) {
            this.instanceInfo = instanceInfo;
            this.source = source;
        }

        public InstanceInfo getInstanceInfo() {
            return instanceInfo;
        }

        public Source getSource() {
            return source;
        }
    }
}
