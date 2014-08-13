package com.netflix.eureka.registry;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

import com.netflix.eureka.SampleInstanceInfo;
import com.netflix.eureka.interests.ChangeNotification;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.Observable;
import rx.Subscriber;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author David Liu
 */
public class DeltaInstanceInfoTest {

    private InstanceInfo baseInstanceInfo;
    private DeltaInstanceInfo deltaInstanceInfo;

    @Rule
    public final ExternalResource instanceInfoResources = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            baseInstanceInfo = SampleInstanceInfo.DiscoveryServer.build();

            assertThat(baseInstanceInfo.getStatus(), equalTo(InstanceInfo.Status.UP));
            assertThat(baseInstanceInfo.getPorts(), containsInAnyOrder(80, 8080));

            deltaInstanceInfo = new DeltaInstanceInfo();
            deltaInstanceInfo.addDelta("status", InstanceInfo.Status.OUT_OF_SERVICE);
            deltaInstanceInfo.addDelta("ports", new HashSet<Integer>(Arrays.asList(111, 222)));
        }
    };

    @Test
    public void testApplyToInstanceInfo() {
        InstanceInfo afterDelta = deltaInstanceInfo.applyTo(baseInstanceInfo);
        assertThat(afterDelta.getStatus(), equalTo(InstanceInfo.Status.OUT_OF_SERVICE));
        assertThat(afterDelta.getPorts(), containsInAnyOrder(111, 222));
    }

    @Test
    public void testGetChangeNotifications() throws Exception {
        Observable<ChangeNotification<InstanceInfo>> changes = deltaInstanceInfo.forChanges(baseInstanceInfo);

        final List<InstanceInfo> changeInfos = new ArrayList<InstanceInfo>();
        final CountDownLatch completionLatch = new CountDownLatch(1);
        changes.subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
            @Override
            public void onCompleted() {
                completionLatch.countDown();
            }

            @Override
            public void onError(Throwable e) {
                completionLatch.countDown();
            }

            @Override
            public void onNext(ChangeNotification<InstanceInfo> notification) {
                assertThat(notification.getKind(), equalTo(ChangeNotification.Kind.Modify));
                changeInfos.add(notification.getData());
            }
        });

        completionLatch.await(1, TimeUnit.MINUTES);

        assertThat(changeInfos.size(), equalTo(2));
        for (InstanceInfo change : changeInfos) {
            if (change.getStatus() == InstanceInfo.Status.UP) {
                assertThat(change.getPorts(), containsInAnyOrder(111, 222));
            } else {
                assertThat(change.getPorts(), containsInAnyOrder(80, 8080));
                assertThat(change.getStatus(), equalTo(InstanceInfo.Status.OUT_OF_SERVICE));
            }
        }
    }
}
