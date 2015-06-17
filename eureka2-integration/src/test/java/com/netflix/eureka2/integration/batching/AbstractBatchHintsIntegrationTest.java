package com.netflix.eureka2.integration.batching;

import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.interests.Interest;
import com.netflix.eureka2.interests.StreamStateNotification;
import com.netflix.eureka2.protocol.interest.AddInstance;
import com.netflix.eureka2.protocol.interest.InterestSetNotification;
import com.netflix.eureka2.protocol.interest.StreamStateUpdate;
import com.netflix.eureka2.registry.MultiSourcedDataHolder;
import com.netflix.eureka2.registry.Source;
import com.netflix.eureka2.registry.SourcedEurekaRegistry;
import com.netflix.eureka2.registry.instance.InstanceInfo;

import java.util.ArrayList;
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

    protected void verifyRegistryContentContainOnlySource(SourcedEurekaRegistry<InstanceInfo> registry, Source source) {
        List<? extends MultiSourcedDataHolder<InstanceInfo>> holders = registry.getHolders().toList().toBlocking().first();
        for (MultiSourcedDataHolder<InstanceInfo> holder : holders) {
            assertThat(holder.getAllSources().size(), is(1));
            assertThat(holder.getAllSources().iterator().next(), is(source));
        }
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
