package com.netflix.eureka2.server.service.overrides;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.testkit.data.builder.SampleOverrides;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import rx.Scheduler;
import rx.observers.TestSubscriber;
import rx.schedulers.Schedulers;
import rx.schedulers.TestScheduler;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static com.netflix.eureka2.interests.ChangeNotification.Kind.*;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author David Liu
 */
public abstract class OverridesRegistryTest {

    protected TestScheduler testScheduler;
    protected OverridesRegistry overridesRegistry;

    @Before
    public void setUp() {
        this.testScheduler = Schedulers.test();
        this.overridesRegistry = getOverridesRegistry(testScheduler);
    }

    @After
    public void tearDown() {
        if (overridesRegistry != null) {
            overridesRegistry.shutdown();
        }
    }

    @Test
    public void testGetSetRemove() {
        String id1 = "id1";

        Overrides overrides1 = SampleOverrides.generateOverrides(id1, 2);
        overridesRegistry.set(overrides1);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);  // advance time for the local cache to load
        assertThat(overridesRegistry.get(id1), is(equalTo(overrides1)));

        Overrides overrides1update = SampleOverrides.generateOverrides(id1, 3);
        overridesRegistry.set(overrides1update);
        assertThat(overridesRegistry.get(id1), is(equalTo(overrides1update)));

        String id2 = "id2";

        Overrides overrides2 = SampleOverrides.generateOverrides(id2, 2);
        overridesRegistry.set(overrides2);
        assertThat(overridesRegistry.get(id2), is(equalTo(overrides2)));

        Overrides overrides2update = SampleOverrides.generateOverrides(id2, 1);
        overridesRegistry.set(overrides2update);
        assertThat(overridesRegistry.get(id2), is(equalTo(overrides2update)));

        overridesRegistry.remove(id2);
        assertThat(overridesRegistry.get(id2), is(equalTo(null)));
    }

    @Test
    public void testForUpdatesSingleInstance() {
        String idA = "idA";
        String idB = "idB";
        TestSubscriber<ChangeNotification<Overrides>> testSubscriber = new TestSubscriber<>();
        overridesRegistry.forUpdates(idA).subscribe(testSubscriber);

        testScheduler.triggerActions();
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0).getKind(), is(Delete));
        assertThat(testSubscriber.getOnNextEvents().get(0).getData().getId(), is(idA));

        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber.getOnNextEvents().get(0).getKind(), is(Delete));
        assertThat(testSubscriber.getOnNextEvents().get(0).getData().getId(), is(idA));

        Overrides overrides1 = SampleOverrides.generateOverrides(idA, 2);
        overridesRegistry.set(overrides1);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber.getOnNextEvents().size(), is(2));
        assertThat(testSubscriber.getOnNextEvents().get(1).getKind(), is(Add));
        assertThat(testSubscriber.getOnNextEvents().get(1).getData(), is(equalTo(overrides1)));

        Overrides overrides1update = SampleOverrides.generateOverrides(idA, 1);
        overridesRegistry.set(overrides1update);
        Overrides overrides2 = SampleOverrides.generateOverrides(idB, 1);
        overridesRegistry.set(overrides2);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber.getOnNextEvents().size(), is(3));
        assertThat(testSubscriber.getOnNextEvents().get(2).getKind(), is(Modify));
        assertThat(testSubscriber.getOnNextEvents().get(2).getData(), is(equalTo(overrides1update)));

        overridesRegistry.remove(idA);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber.getOnNextEvents().size(), is(4));
        assertThat(testSubscriber.getOnNextEvents().get(3).getKind(), is(Delete));
        assertThat(testSubscriber.getOnNextEvents().get(3).getData().getId(), is(equalTo(idA)));
    }

    @Test
    public void testForUpdatesSingleInstanceMultipleSubscription() {
        String idA = "idA";
        String idB = "idB";
        TestSubscriber<ChangeNotification<Overrides>> testSubscriber1 = new TestSubscriber<>();
        TestSubscriber<ChangeNotification<Overrides>> testSubscriber2 = new TestSubscriber<>();

        overridesRegistry.forUpdates(idA).subscribe(testSubscriber1);
        overridesRegistry.forUpdates(idA).subscribe(testSubscriber2);

        testScheduler.triggerActions();
        assertThat(testSubscriber1.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber1.getOnNextEvents().get(0).getKind(), is(Delete));
        assertThat(testSubscriber1.getOnNextEvents().get(0).getData().getId(), is(idA));

        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber1.getOnNextEvents().size(), is(1));
        assertThat(testSubscriber1.getOnNextEvents().get(0).getKind(), is(Delete));
        assertThat(testSubscriber1.getOnNextEvents().get(0).getData().getId(), is(idA));

        Overrides overrides1 = SampleOverrides.generateOverrides(idA, 2);
        overridesRegistry.set(overrides1);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber1.getOnNextEvents().size(), is(2));
        assertThat(testSubscriber1.getOnNextEvents().get(1).getKind(), is(Add));
        assertThat(testSubscriber1.getOnNextEvents().get(1).getData(), is(equalTo(overrides1)));

        Overrides overrides1update = SampleOverrides.generateOverrides(idA, 1);
        overridesRegistry.set(overrides1update);
        Overrides overrides2 = SampleOverrides.generateOverrides(idB, 1);
        overridesRegistry.set(overrides2);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber1.getOnNextEvents().size(), is(3));
        assertThat(testSubscriber1.getOnNextEvents().get(2).getKind(), is(Modify));
        assertThat(testSubscriber1.getOnNextEvents().get(2).getData(), is(equalTo(overrides1update)));

        overridesRegistry.remove(idA);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);
        assertThat(testSubscriber1.getOnNextEvents().size(), is(4));
        assertThat(testSubscriber1.getOnNextEvents().get(3).getKind(), is(Delete));
        assertThat(testSubscriber1.getOnNextEvents().get(3).getData().getId(), is(equalTo(idA)));

        testSubscriber2.assertReceivedOnNext(testSubscriber1.getOnNextEvents());
    }

    @Test
    public void testForUpdatesMultipleInstances() {
        String idA = "idA";
        String idB = "idB";
        TestSubscriber<ChangeNotification<Overrides>> testSubscriberA = new TestSubscriber<>();
        TestSubscriber<ChangeNotification<Overrides>> testSubscriberB = new TestSubscriber<>();
        overridesRegistry.forUpdates(idA).subscribe(testSubscriberA);
        testScheduler.triggerActions();

        Overrides overridesA = SampleOverrides.generateOverrides(idA, 2);
        overridesRegistry.set(overridesA);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);

        Overrides overridesAupdate1 = SampleOverrides.generateOverrides(idA, 1);
        overridesRegistry.set(overridesAupdate1);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);

        assertThat(testSubscriberA.getOnNextEvents().size(), is(3));
        assertThat(testSubscriberA.getOnNextEvents().get(0), equalTo(new ChangeNotification<>(Delete, new Overrides(idA, Collections.EMPTY_SET))));
        assertThat(testSubscriberA.getOnNextEvents().get(1), equalTo(new ChangeNotification<>(Add, overridesA)));
        assertThat(testSubscriberA.getOnNextEvents().get(2).getKind(), equalTo(Modify));
        assertThat(testSubscriberA.getOnNextEvents().get(2).getData(), equalTo(overridesAupdate1));

        assertThat(testSubscriberB.getOnNextEvents(), is(empty()));

        overridesRegistry.forUpdates(idB).subscribe(testSubscriberB);

        Overrides overridesAupdate2 = SampleOverrides.generateOverrides(idA, 3);
        overridesRegistry.set(overridesAupdate2);

        Overrides overridesB = SampleOverrides.generateOverrides(idB, 1);
        overridesRegistry.set(overridesB);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);

        Overrides overridesBupdate1 = SampleOverrides.generateOverrides(idB, 2);
        overridesRegistry.set(overridesBupdate1);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);

        assertThat(testSubscriberA.getOnNextEvents().size(), is(4));
        assertThat(testSubscriberA.getOnNextEvents().get(3).getKind(), equalTo(Modify));
        assertThat(testSubscriberA.getOnNextEvents().get(3).getData(), equalTo(overridesAupdate2));

        assertThat(testSubscriberB.getOnNextEvents().size(), is(3));
        assertThat(testSubscriberB.getOnNextEvents().get(0), equalTo(new ChangeNotification<>(Delete, new Overrides(idB, Collections.EMPTY_SET))));
        assertThat(testSubscriberB.getOnNextEvents().get(1), equalTo(new ChangeNotification<>(Add, overridesB)));
        assertThat(testSubscriberB.getOnNextEvents().get(2).getKind(), equalTo(Modify));
        assertThat(testSubscriberB.getOnNextEvents().get(2).getData(), equalTo(overridesBupdate1));

        overridesRegistry.remove(idB);
        testScheduler.advanceTimeBy(30, TimeUnit.SECONDS);

        assertThat(testSubscriberA.getOnNextEvents().size(), is(4));  // no change

        assertThat(testSubscriberB.getOnNextEvents().size(), is(4));
        assertThat(testSubscriberB.getOnNextEvents().get(3).getKind(), is(Delete));
        assertThat(testSubscriberB.getOnNextEvents().get(3).getData().getId(), is(equalTo(idB)));
    }


    abstract OverridesRegistry getOverridesRegistry(Scheduler scheduler);
}
