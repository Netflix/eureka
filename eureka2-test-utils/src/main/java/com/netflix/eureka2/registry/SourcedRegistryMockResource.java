package com.netflix.eureka2.registry;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.Interests;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.rules.ExternalResource;
import org.mockito.Mockito;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;
import rx.Observable;
import rx.Subscriber;
import rx.subjects.ReplaySubject;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

/**
 * Provides {@link EurekaRegistry} mock with a set of helper methods to control it.
 *
 * @author Tomasz Bak
 */
public class SourcedRegistryMockResource extends ExternalResource {

    private final EurekaRegistry<InstanceInfo> registry = Mockito.mock(EurekaRegistry.class);
    private volatile ReplaySubject<ChangeNotification<InstanceInfo>> fullRegistrySubject = ReplaySubject.create();
    private final Map<Interest, ReplaySubject<ChangeNotification<InstanceInfo>>> notificationSubjects = new HashMap<>();

    public EurekaRegistry<InstanceInfo> registry() {
        return registry;
    }

    @Override
    protected void before() throws Throwable {
        when(registry.forInterest(any(Interest.class))).thenAnswer(new Answer<Observable<ChangeNotification<InstanceInfo>>>() {
            @Override
            public Observable<ChangeNotification<InstanceInfo>> answer(InvocationOnMock invocation) throws Throwable {
                return getInterestSubject((Interest) invocation.getArguments()[0]);
            }
        });
        fullRegistrySubject = ReplaySubject.create();
    }

    /**
     * Complete current notification subject, and create a new one. All interest subscribers will onComplete.
     */
    public void reset() {
        fullRegistrySubject.onCompleted();
        notificationSubjects.clear();

        Mockito.reset(registry);
        fullRegistrySubject = ReplaySubject.create();
    }

    public void batchStart() {
        batchStart(Interests.forFullRegistry());
    }

    public void batchStart(Interest<InstanceInfo> interest) {
        getInterestSubject(interest).onNext(StreamStateNotification.bufferStartNotification(interest));
    }

    public void batchStartFor(InstanceInfo instanceInfo) {
        for (Entry<Interest, ReplaySubject<ChangeNotification<InstanceInfo>>> entry : notificationSubjects.entrySet()) {
            if (entry.getKey().matches(instanceInfo)) {
                entry.getValue().onNext(StreamStateNotification.bufferStartNotification(entry.getKey()));
            }
        }
    }

    public void batchEnd() {
        batchEnd(Interests.forFullRegistry());
    }

    public void batchEnd(Interest<InstanceInfo> interest) {
        getInterestSubject(interest).onNext(StreamStateNotification.bufferEndNotification(interest));
    }

    public void batchEndFor(InstanceInfo instanceInfo) {
        for (Entry<Interest, ReplaySubject<ChangeNotification<InstanceInfo>>> entry : notificationSubjects.entrySet()) {
            if (entry.getKey().matches(instanceInfo)) {
                entry.getValue().onNext(StreamStateNotification.bufferEndNotification(entry.getKey()));
            }
        }
    }

    public void uploadToRegistry(InstanceInfo sample) {
        fullRegistrySubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, sample));
    }

    public void uploadBatchToRegistry(Interest<InstanceInfo> interest, InstanceInfo sample) {
        batchStart(interest);
        fullRegistrySubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, sample));
        batchEnd(interest);
    }

    public void removeFromRegistry(InstanceInfo sample) {
        fullRegistrySubject.onNext(new ChangeNotification<InstanceInfo>(Kind.Delete, sample));
    }

    /**
     * Upload a given number of instances to the registry, forged from the provided
     * template.
     */
    public void uploadClusterToRegistry(InstanceInfo template, int size) {
        Iterator<InstanceInfo> instanceIt = SampleInstanceInfo.collectionOf(template.getApp(), template);
        for (int i = 0; i < size; i++) {
            uploadToRegistry(instanceIt.next());
        }
    }

    /**
     * Upload a given number of instances to the registry.
     */
    public String uploadClusterToRegistry(SampleInstanceInfo sample, int size) {
        String appName = null;
        for (InstanceInfo item : sample.clusterOf(size)) {
            appName = item.getApp();
            uploadToRegistry(item);
        }
        return appName;
    }

    public void uploadClusterBatchToRegistry(InstanceInfo template, int size) {
        Iterator<InstanceInfo> instanceIt = SampleInstanceInfo.collectionOf(template.getApp(), template);

        InstanceInfo first = instanceIt.next();
        batchStartFor(first);
        uploadToRegistry(first);

        for (int i = 1; i < size; i++) {
            uploadToRegistry(instanceIt.next());
        }

        batchEndFor(first);
    }

    public String uploadClusterBatchToRegistry(SampleInstanceInfo sample, int size) {
        InstanceInfo first = null;
        for (InstanceInfo item : sample.clusterOf(size)) {
            if (first == null) {
                first = item;
                batchStartFor(item);
            }
            uploadToRegistry(item);
        }
        batchEndFor(first);
        return first.getApp();
    }

    private ReplaySubject<ChangeNotification<InstanceInfo>> getInterestSubject(final Interest<InstanceInfo> interest) {
        ReplaySubject<ChangeNotification<InstanceInfo>> subject = notificationSubjects.get(interest);
        if (subject == null) {
            subject = ReplaySubject.create();
            final ReplaySubject<ChangeNotification<InstanceInfo>> finalSubject = subject;
            fullRegistrySubject.subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                @Override
                public void onCompleted() {
                    finalSubject.onCompleted();
                }

                @Override
                public void onError(Throwable e) {
                    finalSubject.onError(e);
                }

                @Override
                public void onNext(ChangeNotification<InstanceInfo> notification) {
                    if (notification.isDataNotification() && interest.matches(notification.getData())) {
                        finalSubject.onNext(notification);
                    }
                }
            });
            notificationSubjects.put(interest, subject);
        }
        return subject;
    }
}
