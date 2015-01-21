package com.netflix.eureka2.interests;

import java.util.Collections;

import com.netflix.eureka2.interests.Index.InitStateHolder;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleChangeNotification;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class IndexRegistryImplTest {

    private final IndexRegistryImpl<InstanceInfo> indexRegistry = new IndexRegistryImpl<>();

    private final PublishSubject<ChangeNotification<InstanceInfo>> dataSource = PublishSubject.create();

    private final InitStateHolder<InstanceInfo> initStateHolder =
            new InstanceInfoInitStateHolder(Collections.<ChangeNotification<InstanceInfo>>emptyIterator());

    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> testSubscriber = new ExtTestSubscriber<>();

    @Test
    public void testCleansUpResourcesOnShutdown() throws Exception {
        indexRegistry.forInterest(Interests.forFullRegistry(), dataSource, initStateHolder).subscribe(testSubscriber);

        // Add first item
        dataSource.onNext(SampleChangeNotification.DiscoveryAdd.newNotification());
        assertThat(testSubscriber.takeNext(), is(notNullValue()));

        // Now shutdown registry
        indexRegistry.shutdown();

        // Subscription stream should complete
        testSubscriber.assertOnCompleted();
    }

    @Test
    public void testSendsErrorToSubscribersWhenShutdownWithError() throws Exception {
        indexRegistry.forInterest(Interests.forFullRegistry(), dataSource, initStateHolder).subscribe(testSubscriber);

        // Add first item
        dataSource.onNext(SampleChangeNotification.DiscoveryAdd.newNotification());
        assertThat(testSubscriber.takeNext(), is(notNullValue()));

        // Now shutdown registry
        Exception error = new Exception();
        indexRegistry.shutdown(error);

        // Subscription stream should complete
        testSubscriber.assertOnError(error);
    }
}