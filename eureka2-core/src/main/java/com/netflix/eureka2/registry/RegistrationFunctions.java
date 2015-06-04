package com.netflix.eureka2.registry;

import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.utils.rx.RxFunctions;
import rx.Notification;
import rx.Notification.Kind;
import rx.Observable;
import rx.functions.Func1;

import static com.netflix.eureka2.utils.rx.RxFunctions.filterNullValuesFunc;

/**
 * A collection of functions operating on registration streams.
 *
 * @author Tomasz Bak
 */
public final class RegistrationFunctions {

    private RegistrationFunctions() {
    }

    /**
     * Convert registration observable of {@link InstanceInfo} objects, into change notification stream, with
     * a sequence of Kind.Add updates, followed by Kind.Delete when stream onCompletes or onErrors.
     * Errors are not propagated in the returned stream.
     */
    public static Observable<ChangeNotification<InstanceInfoWithSource>> toSourcedChangeNotificationStream(Observable<InstanceInfo> registrationUpdates, final Source source) {
        return registrationUpdates
                .materialize()
                .compose(RxFunctions.<Notification<InstanceInfo>>lastWindow(2))
                .map(new Func1<List<Notification<InstanceInfo>>, ChangeNotification<InstanceInfoWithSource>>() {
                    @Override
                    public ChangeNotification<InstanceInfoWithSource> call(List<Notification<InstanceInfo>> notifications) {
                        Notification<InstanceInfo> first = notifications.get(0);
                        if (notifications.size() == 1) {
                            if (first.getKind() == Kind.OnNext) {
                                return new ChangeNotification<>(ChangeNotification.Kind.Add, new InstanceInfoWithSource(first.getValue(), source));
                            }
                            return null;
                        }
                        Notification<InstanceInfo> second = notifications.get(1);
                        if (second.getKind() == Kind.OnNext) {
                            return new ChangeNotification<>(ChangeNotification.Kind.Add, new InstanceInfoWithSource(second.getValue(), source));
                        }
                        return new ChangeNotification<>(ChangeNotification.Kind.Delete, new InstanceInfoWithSource(first.getValue(), source));
                    }
                })
                .filter(filterNullValuesFunc());
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
