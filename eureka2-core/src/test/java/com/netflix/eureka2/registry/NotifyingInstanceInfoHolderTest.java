package com.netflix.eureka2.registry;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;

/**
 * @author David Liu
 */
public class NotifyingInstanceInfoHolderTest {

    private final PublishSubject<ChangeNotification<InstanceInfo>> notificationSubject = PublishSubject.create();
    private final ExtTestSubscriber<ChangeNotification<InstanceInfo>> notificationSubscriber = new ExtTestSubscriber<>();

    private final Source localSource = new Source(Source.Origin.LOCAL);
    private final InstanceInfo.Builder builder = SampleInstanceInfo.DiscoveryServer.builder();

    InstanceInfo firstInfo = builder.withStatus(InstanceInfo.Status.STARTING).build();
    InstanceInfo secondInfo = builder.withStatus(InstanceInfo.Status.UP).build();
    InstanceInfo thirdInfo = builder.withStatus(InstanceInfo.Status.DOWN).build();

    private final NotifyingInstanceInfoHolder holder = new NotifyingInstanceInfoHolder(
            notificationSubject, firstInfo.getId(), registryMetrics().getEurekaServerRegistryMetrics()
    );

    @Before
    public void setUp() throws Exception {
        notificationSubject.subscribe(notificationSubscriber);
    }

    @Test
    public void testAddFirst() throws Exception {
        holder.update(localSource, firstInfo);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.getSource(), equalTo(localSource));
        assertThat(notificationSubscriber.takeNextOrFail(), is(addChangeNotificationOf(firstInfo)));
    }

    @Test
    public void testUpdateSameSource() throws Exception {
        injectFirst();

        holder.update(localSource, secondInfo);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(notificationSubscriber.takeNextOrFail(), is(modifyChangeNotificationOf(secondInfo)));
    }

    @Test
    public void testUpdateSameSourceDiffIdAlsoUpdateSnapshot() throws Exception {
        injectFirst();

        InstanceInfo secondInfo = builder.withStatus(InstanceInfo.Status.UP).build();
        Source newLocalSource = new Source(localSource.getOrigin(), localSource.getName());
        holder.update(newLocalSource, secondInfo);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.getSource(), equalTo(newLocalSource));
        assertThat(notificationSubscriber.takeNextOrFail(), is(modifyChangeNotificationOf(secondInfo)));
    }

    @Test
    public void testUpdateDifferentSources() throws Exception {
        injectFirst();

        InstanceInfo secondInfo = builder.withStatus(InstanceInfo.Status.UP).build();

        Source fooSource = new Source(Source.Origin.REPLICATED, "foo");
        holder.update(fooSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));


        holder.update(fooSource, thirdInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(thirdInfo));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testUpdateDifferentSourcesPromoteLocal() throws Exception {
        Source fooSource = new Source(Source.Origin.REPLICATED, "foo");

        holder.update(fooSource, firstInfo);
        assertThat(notificationSubscriber.takeNextOrFail(), is(addChangeNotificationOf(firstInfo)));

        // verify promotion of local source happened
        holder.update(localSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.get(fooSource), equalTo(firstInfo));
        assertThat(notificationSubscriber.takeNextOrFail(), is(addChangeNotificationOf(secondInfo)));

        // Now modify foo source, which should not trigger any notification changes
        holder.update(fooSource, thirdInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.get(fooSource), equalTo(thirdInfo));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));

        // Now modify local source, which should trigger notification
        InstanceInfo fourthInfo = builder.withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();

        holder.update(localSource, fourthInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(fourthInfo));
        assertThat(holder.get(localSource), equalTo(fourthInfo));
        assertThat(notificationSubscriber.takeNextOrFail(), is(modifyChangeNotificationOf(fourthInfo)));
    }

    @Test
    public void testRemoveSameSource() throws Exception {
        injectFirst();

        holder.remove(localSource);

        assertThat(holder.size(), equalTo(0));
        assertThat(holder.get(), equalTo(null));
        assertThat(holder.get(localSource), equalTo(null));
        assertThat(notificationSubscriber.takeNextOrFail(), is(deleteChangeNotificationOf(firstInfo)));
    }

    @Test
    public void testRemoveExistingSourceWithDifferentId() throws Exception {
        injectFirst();

        Source newLocalSource = new Source(localSource.getOrigin(), localSource.getName());
        boolean removeStatus = holder.remove(newLocalSource);

        assertThat(removeStatus, is(false));
        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(localSource), equalTo(firstInfo));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testRemoveNonSnapshotCopy() throws Exception {
        injectFirst();

        // Add non-snapshot copy
        Source fooSource = new Source(Source.Origin.REPLICATED, "foo");
        holder.update(fooSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));

        // Remove non-snapshot copy
        holder.remove(fooSource);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(null));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));
    }

    @Test
    public void testRemoveSnapshotCopyPromoteAnother() throws Exception {
        injectFirst();

        // Add second
        Source fooSource = new Source(Source.Origin.REPLICATED, "foo");
        holder.update(fooSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(notificationSubscriber.takeNext(), is(nullValue()));

        // Now remove snapshot
        holder.remove(localSource);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(holder.get(localSource), not(equalTo(secondInfo)));
        assertThat(notificationSubscriber.takeNextOrFail(), is(deleteChangeNotificationOf(firstInfo)));
        assertThat(notificationSubscriber.takeNextOrFail(), is(addChangeNotificationOf(secondInfo)));
    }

    private void injectFirst() {
        holder.update(localSource, firstInfo);
        notificationSubscriber.takeNext();
    }
}
