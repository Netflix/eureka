package com.netflix.eureka.aws;

import com.amazonaws.services.ec2.AmazonEC2;
import com.netflix.appinfo.ApplicationInfoManager;
import com.netflix.discovery.EurekaClient;
import com.netflix.discovery.EurekaClientConfig;
import com.netflix.eureka.EurekaServerConfig;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;

/**
 * TODO PeerAwareInstanceRegistry has complex dependencies and that prevents unit testing {@link VpcEniManager}.
 * There is a refactoring on branch that fixes that. Once it is merged this test can be completed.
 *
 * @author Tomasz Bak
 */
public class VpcEniManagerTest {

//    private static final InstanceInfo MY_INSTANCE = InstanceInfoGenerator.takeOne();

    private final EurekaClientConfig eurekaClientConfig = mock(EurekaClientConfig.class);
    private final EurekaServerConfig eurekaServerConfig = mock(EurekaServerConfig.class);
    private final EurekaClient eurekaClient = mock(EurekaClient.class);
    private final ApplicationInfoManager infoManager = mock(ApplicationInfoManager.class);
    //    private final PeerAwareInstanceRegistry registry = mock(PeerAwareInstanceRegistry.class);
    private final AmazonEC2 amazonEC2 = mock(AmazonEC2.class);

    private VpcEniManager vpcEniManager;

    @Before
    public void setUp() throws Exception {
        vpcEniManager = new VpcEniManager(
                eurekaClientConfig,
                eurekaServerConfig,
                eurekaClient,
                infoManager,
                null,
                amazonEC2
        );
    }

    @Test
    @Ignore
    public void testEniIsAllocatedIfNotPresentYet() throws Exception {
        assertThat(vpcEniManager.isEIPBound(), is(false));
        vpcEniManager.start();
        assertThat(vpcEniManager.isEIPBound(), is(true));
    }

    @Test
    @Ignore
    public void testEniAllocationIsRetriedUntilSucceeds() throws Exception {
    }

    @Test
    @Ignore
    public void testEniIsAllocatedAgainIfWasExternallyUnbound() throws Exception {
    }

    @Test
    @Ignore
    public void testEniIsNotAllocatedAgainIfAlreadyBound() throws Exception {
    }

    @Test
    @Ignore
    public void testOnShutdownEniIsUnboundIfAttached() throws Exception {
    }
}