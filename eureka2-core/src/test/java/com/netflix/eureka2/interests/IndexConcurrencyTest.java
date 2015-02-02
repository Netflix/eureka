package com.netflix.eureka2.interests;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import com.netflix.eureka2.interests.ChangeNotification.Kind;
import com.netflix.eureka2.junit.categories.LongRunningTest;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import rx.functions.Action1;
import rx.subjects.PublishSubject;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

/**
 * This test verifies correctness of processing in {@link Index} object.
 * Originally it revealed two concurrency issues, that are now fixed.
 *
 * @author Tomasz Bak
 */
@Category(LongRunningTest.class)
public class IndexConcurrencyTest {

    private static final int NOTIFICATIONS = 10000;

    @Test(timeout = 60000)
    public void testRaceConditionsDoNotHappen() throws Exception {
        PublishSubject<ChangeNotification<InstanceInfo>> dataSource = PublishSubject.create();

        InstanceInfoInitStateHolder initStateHolder = new InstanceInfoInitStateHolder(
                Collections.<ChangeNotification<InstanceInfo>>emptyIterator(), Interests.forFullRegistry());

        Index<InstanceInfo> index = Index.forInterest(Interests.forFullRegistry(), dataSource, initStateHolder);

        // We subscribe in the middle of notification stream
        for (int i = 0; i < NOTIFICATIONS / 2; i++) {
            InstanceInfo data = SampleInstanceInfo.DiscoveryServer.builder().withId(Integer.toString(i)).build();
            dataSource.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, data));
        }
        IndexSubscriber indexSubscriber = new IndexSubscriber(index);
        indexSubscriber.start();

        // Now second batch
        int id = NOTIFICATIONS / 2;
        for (int i = 0; i < NOTIFICATIONS / 2; i++) {
            InstanceInfo data = SampleInstanceInfo.DiscoveryServer.builder().withId(Integer.toString(id)).build();
            dataSource.onNext(new ChangeNotification<InstanceInfo>(Kind.Add, data));
            id++;
        }

        // Wait for subscriber to terminate
        indexSubscriber.join();

        // Verify that we got all items (may be duplicates).
        List<Integer> idxIds = indexSubscriber.getIds();
        int[] matched = new int[NOTIFICATIONS];
        Arrays.fill(matched, 0, matched.length, 0);
        for (Integer idxId : idxIds) {
            int value = idxId;
            matched[value]++;
        }
        int missed = 0;
        for (int i = 0; i < matched.length; i++) {
            if (matched[i] != 1) {
                if (matched[i] == 0) {
                    missed++;
                }
//                System.out.println("At position " + i + " found " + matched[i] + " items");
            }
        }

        // Verify we got everything
        assertThat(missed, is(equalTo(0)));
    }

    private static class IndexSubscriber extends Thread {
        private final Index<InstanceInfo> index;
        private final List<Integer> ids = new ArrayList<>(NOTIFICATIONS);

        private IndexSubscriber(Index<InstanceInfo> index) {
            this.index = index;
        }

        public List<Integer> getIds() {
            return ids;
        }

        @Override
        public void run() {
            index.subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                @Override
                public void call(ChangeNotification<InstanceInfo> notification) {
                    ids.add(Integer.parseInt(notification.getData().getId()));
                }
            });

        }
    }
}