package com.netflix.eureka2.integration.batching;

import com.netflix.eureka2.model.notification.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.model.notification.StreamStateNotification;
import com.netflix.eureka2.protocol.common.AddInstance;
import com.netflix.eureka2.protocol.common.InterestSetNotification;
import com.netflix.eureka2.protocol.common.StreamStateUpdate;
import com.netflix.eureka2.registry.EurekaRegistry;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.model.Source;
import com.netflix.eureka2.model.instance.InstanceInfo;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.is;

/**
 * @author David Liu
 */
public abstract class AbstractBatchHintsIntegrationTest {

    //
    // ----- helpers -----
    //

    protected void verifyRegistryContentContainOnlySource(EurekaRegistry<InstanceInfo> registry, Source source) {
        List<? extends MultiSourcedDataHolder<InstanceInfo>> holders = registry.getHolders().toList().toBlocking().first();
        for (MultiSourcedDataHolder<InstanceInfo> holder : holders) {
            assertThat(holder.getAllSources().size(), is(1));
            assertThat(holder.getAllSources().iterator().next(), is(source));
        }
    }

    protected void verifyRegistryContentSourceEntries(EurekaRegistry<InstanceInfo> registry, Source source, int entries) {
        List<? extends MultiSourcedDataHolder<InstanceInfo>> holders = registry.getHolders().toList().toBlocking().first();
        Collection<Source> matches = new ArrayList<>();
        for (MultiSourcedDataHolder<InstanceInfo> holder : holders) {
            if (holder.getAllSources().contains(source)) {
                matches.add(source);
            }
        }
        assertThat(matches.size(), is(entries));
    }

    protected List<ChangeNotification<InstanceInfo>> toChangeNotifications(List<InterestSetNotification> interestSetNotifications) {
        List<ChangeNotification<InstanceInfo>> toReturn = new ArrayList<>(interestSetNotifications.size());
        for (InterestSetNotification n : interestSetNotifications) {
            if (n instanceof StreamStateUpdate) {
                if (((StreamStateUpdate) n).getState() == StreamStateNotification.BufferState.BufferEnd) {
                    toReturn.add(ChangeNotification.<InstanceInfo>bufferSentinel());
                } else {
                    // ignore buffer start
                }
            } else if (n instanceof AddInstance) {
                toReturn.add(new ChangeNotification<>(ChangeNotification.Kind.Add, ((AddInstance) n).getInstanceInfo()));
            }
        }

        return toReturn;
    }

    protected StreamStateUpdate newBufferStart(Interest<InstanceInfo> interest) {
        return new StreamStateUpdate(StreamStateNotification.bufferStartNotification(interest));
    }

    protected StreamStateUpdate newBufferEnd(Interest<InstanceInfo> interest) {
        return new StreamStateUpdate(StreamStateNotification.bufferEndNotification(interest));
    }
}
