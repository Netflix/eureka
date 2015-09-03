package com.netflix.eureka2.registry;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.model.notification.ChangeNotification.Kind;
import com.netflix.eureka2.metric.EurekaRegistryMetrics;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;
import static org.mockito.Mockito.mock;

/**
 * @author David Liu
 */
public class SimpleInstanceInfoDataStoreTest {

    private final EurekaRegistryMetrics metrics = mock(EurekaRegistryMetrics.class);
    private final SimpleInstanceInfoDataStore dataStore = new SimpleInstanceInfoDataStore(metrics);

    private final Source localSource = new Source(Source.Origin.LOCAL, "local");
    private final Source remoteSource1 = new Source(Source.Origin.REPLICATED, "A");
    private final Source remoteSource2 = new Source(Source.Origin.REPLICATED, "B");

    private final InstanceInfo.Builder templateA = SampleInstanceInfo.ZuulServer.builder().withId("A");
    private final InstanceInfo.Builder templateB = SampleInstanceInfo.WebServer.builder().withId("B");

    private final InstanceInfo infoA_1 = templateA.withAsg("1").build();
    private final InstanceInfo infoA_2 = templateA.withAsg("2").build();
    private final InstanceInfo infoA_3 = templateA.withAsg("3").build();

    private final InstanceInfo infoB_1 = templateB.withAsg("1").build();
    private final InstanceInfo infoB_2 = templateB.withAsg("2").build();
    private final InstanceInfo infoB_3 = templateA.withAsg("3").build();

    private ChangeNotification<InstanceInfo>[] notifications;
    @Test
    public void testUpdate() {
        // add data A from local source
        notifications = dataStore.update(infoA_1, localSource);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Add));
        assertThat(notifications[0].getData(), is(infoA_1));
        assertThat(dataStore.size(), is(1));

        // add data B with a diff id from a remote source
        notifications = dataStore.update(infoB_1, remoteSource1);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Add));
        assertThat(notifications[0].getData(), is(infoB_1));
        assertThat(dataStore.size(), is(2));

        // add an (update) data A from local source
        notifications = dataStore.update(infoA_2, localSource);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Modify));
        assertThat(notifications[0].getData(), is(infoA_2));
        assertThat(dataStore.size(), is(2));

        // add an (update) data A from a remote source
        notifications = dataStore.update(infoA_3, remoteSource1);
        assertThat(notifications.length, is(0));
        assertThat(dataStore.size(), is(2));

        // add an (update) data B from a different remote source
        notifications = dataStore.update(infoB_2, remoteSource2);
        assertThat(notifications.length, is(0));
        assertThat(dataStore.size(), is(2));

        // add an (update) data B from the local source
        notifications = dataStore.update(infoB_3, localSource);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Modify));
        assertThat(notifications[0].getData(), is(infoB_3));
        assertThat(dataStore.size(), is(2));
    }

    @Test
    public void testRemove() {
        // pre add some data for removal testing
        dataStore.update(infoA_1, localSource);
        dataStore.update(infoA_2, remoteSource1);
        dataStore.update(infoA_3, remoteSource2);

        dataStore.update(infoB_1, localSource);
        dataStore.update(infoB_2, remoteSource1);
        dataStore.update(infoB_3, remoteSource2);

        // remove data A from local source
        notifications = dataStore.remove(infoA_1.getId(), localSource);
        assertThat(notifications.length, is(2));
        assertThat(notifications[0].getKind(), is(Kind.Delete));
        assertThat(notifications[0].getData(), is(infoA_1));
        assertThat(notifications[1].getKind(), is(Kind.Add));
        assertThat(notifications[1].getData(), is(infoA_2));

        // remove data A from the first head remote source
        notifications = dataStore.remove(infoA_1.getId(), remoteSource1);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Modify));
        assertThat(notifications[0].getData(), is(infoA_3));

        // remove data A from the last source
        notifications = dataStore.remove(infoA_1.getId(), remoteSource2);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Delete));
        assertThat(notifications[0].getData(), is(infoA_3));


        // remove data B from the middle remote source
        notifications = dataStore.remove(infoB_1.getId(), remoteSource1);
        assertThat(notifications.length, is(0));

        // remove data A from the last remote source
        notifications = dataStore.remove(infoB_1.getId(), remoteSource2);
        assertThat(notifications.length, is(0));

        // remove data A from the last (local) source
        notifications = dataStore.remove(infoB_1.getId(), localSource);
        assertThat(notifications.length, is(1));
        assertThat(notifications[0].getKind(), is(Kind.Delete));
        assertThat(notifications[0].getData(), is(infoB_1));
    }
}
