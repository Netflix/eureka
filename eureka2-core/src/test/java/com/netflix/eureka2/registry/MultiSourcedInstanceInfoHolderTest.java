package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.StdSource;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.model.instance.InstanceInfoBuilder;
import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Before;
import org.junit.Test;

import static com.netflix.eureka2.metric.EurekaRegistryMetricFactory.registryMetrics;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.addChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.deleteChangeNotificationOf;
import static com.netflix.eureka2.testkit.junit.EurekaMatchers.modifyChangeNotificationOf;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

/**
 * @author David Liu
 */
public class MultiSourcedInstanceInfoHolderTest {

    private final StdSource localSource = new StdSource(StdSource.Origin.LOCAL);
    private final InstanceInfoBuilder builder = SampleInstanceInfo.DiscoveryServer.builder();

    InstanceInfo firstInfo = builder.withStatus(InstanceInfo.Status.STARTING).build();
    InstanceInfo secondInfo = builder.withStatus(InstanceInfo.Status.UP).build();
    InstanceInfo thirdInfo = builder.withStatus(InstanceInfo.Status.DOWN).build();

    ChangeNotification<InstanceInfo>[] notifications;

    private final MultiSourcedInstanceInfoHolder holder = new MultiSourcedInstanceInfoHolder(
            firstInfo.getId(), registryMetrics().getEurekaServerRegistryMetrics()
    );

    @Before
    public void setUp() throws Exception {
    }

    @Test
    public void testAddFirst() throws Exception {
        notifications = holder.update(localSource, firstInfo);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.getSource(), equalTo(localSource));

        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(addChangeNotificationOf(firstInfo)));
    }

    @Test
    public void testUpdateSameSource() throws Exception {
        injectFirst();

        notifications = holder.update(localSource, secondInfo);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(secondInfo));

        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(modifyChangeNotificationOf(secondInfo)));
    }

    @Test
    public void testUpdateSameSourceDiffIdAlsoUpdateSnapshot() throws Exception {
        injectFirst();

        InstanceInfo secondInfo = builder.withStatus(InstanceInfo.Status.UP).build();
        StdSource newLocalSource = new StdSource(localSource.getOrigin(), localSource.getName());
        notifications = holder.update(newLocalSource, secondInfo);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.getSource(), equalTo(newLocalSource));

        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(modifyChangeNotificationOf(secondInfo)));
    }

    @Test
    public void testUpdateDifferentSources() throws Exception {
        injectFirst();

        InstanceInfo secondInfo = builder.withStatus(InstanceInfo.Status.UP).build();

        StdSource fooSource = new StdSource(StdSource.Origin.REPLICATED, "foo");
        notifications = holder.update(fooSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));

        assertThat(notifications.length, is(0));

        notifications = holder.update(fooSource, thirdInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(thirdInfo));

        assertThat(notifications.length, is(0));
    }

    @Test
    public void testUpdateDifferentSourcesPromoteLocal() throws Exception {
        StdSource fooSource = new StdSource(StdSource.Origin.REPLICATED, "foo");

        notifications = holder.update(fooSource, firstInfo);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(addChangeNotificationOf(firstInfo)));

        // verify promotion of local source happened
        notifications = holder.update(localSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.get(fooSource), equalTo(firstInfo));

        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(addChangeNotificationOf(secondInfo)));

        // Now modify foo source, which should not trigger any notification changes
        notifications = holder.update(fooSource, thirdInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.get(fooSource), equalTo(thirdInfo));

        assertThat(notifications.length, is(0));

        // Now modify local source, which should trigger notification
        InstanceInfo fourthInfo = builder.withStatus(InstanceInfo.Status.OUT_OF_SERVICE).build();

        notifications = holder.update(localSource, fourthInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(fourthInfo));
        assertThat(holder.get(localSource), equalTo(fourthInfo));

        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(modifyChangeNotificationOf(fourthInfo)));
    }

    @Test
    public void testRemoveSameSource() throws Exception {
        injectFirst();

        notifications = holder.remove(localSource);

        assertThat(holder.size(), equalTo(0));
        assertThat(holder.get(), equalTo(null));
        assertThat(holder.get(localSource), equalTo(null));

        assertThat(notifications.length, is(1));
        assertThat(notifications[0], is(deleteChangeNotificationOf(firstInfo)));
    }

    @Test
    public void testRemoveExistingSourceWithDifferentId() throws Exception {
        injectFirst();

        StdSource newLocalSource = new StdSource(localSource.getOrigin(), localSource.getName());
        notifications = holder.remove(newLocalSource);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(localSource), equalTo(firstInfo));

        assertThat(notifications.length, is(0));
    }

    @Test
    public void testRemoveNonSnapshotCopy() throws Exception {
        injectFirst();

        // Add non-snapshot copy
        StdSource fooSource = new StdSource(StdSource.Origin.REPLICATED, "foo");
        notifications = holder.update(fooSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(notifications.length, is(0));

        // Remove non-snapshot copy
        notifications = holder.remove(fooSource);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(null));
        assertThat(notifications.length, is(0));
    }

    @Test
    public void testRemoveSnapshotCopyPromoteAnother() throws Exception {
        injectFirst();

        // Add second
        StdSource fooSource = new StdSource(StdSource.Origin.REPLICATED, "foo");
        notifications = holder.update(fooSource, secondInfo);

        assertThat(holder.size(), equalTo(2));
        assertThat(holder.get(), equalTo(firstInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(notifications.length, is(0));

        // Now remove snapshot
        notifications = holder.remove(localSource);

        assertThat(holder.size(), equalTo(1));
        assertThat(holder.get(), equalTo(secondInfo));
        assertThat(holder.get(fooSource), equalTo(secondInfo));
        assertThat(holder.get(localSource), not(equalTo(secondInfo)));
        assertThat(notifications.length, is(2));
        assertThat(notifications[0], is(deleteChangeNotificationOf(firstInfo)));
        assertThat(notifications[1], is(addChangeNotificationOf(secondInfo)));
    }

    private void injectFirst() {
        holder.update(localSource, firstInfo);
    }
}
