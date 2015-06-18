package com.netflix.eureka2.integration.server.random;

import com.netflix.eureka2.integration.IntegrationTestClassSetup;
import com.netflix.eureka2.interests.ChangeNotification;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;
import static org.hamcrest.number.OrderingComparison.lessThanOrEqualTo;

/**
 * @author David Liu
 */
public abstract class AbstractRandomLifecycleTest extends IntegrationTestClassSetup {

    private static final Logger logger = LoggerFactory.getLogger(AbstractRandomLifecycleTest.class);

    protected void assertLifecycles(List<ChangeNotification<InstanceInfo>> expected, List<ChangeNotification<InstanceInfo>> actual) {
        assertThat(actual.size(), is(lessThanOrEqualTo(expected.size())));
        if (getLast(expected).getKind() == ChangeNotification.Kind.Delete) {
            logger.info("Expecting last item to be delete notification");
            if (actual.size() == 0) {
                logger.info("Expecting last item to be delete and got zero notifications, assuming all compacted");
            } else {
                logger.info("last item {}", getLast(actual));
                assertThat(getLast(actual).getKind(), equalTo(ChangeNotification.Kind.Delete));
            }
        } else {
            logger.info("Expecting last item to be a valid notification");
            logger.info("last item {}", getLast(actual));
            assertThat(getLast(actual).getData(), equalTo(getLast(expected).getData()));
        }
    }

    public <T> T getLast(List<T> list) {
        if (list == null || list.size() == 0) {
            return null;
        }
        return list.get(list.size()-1);
    }
}
