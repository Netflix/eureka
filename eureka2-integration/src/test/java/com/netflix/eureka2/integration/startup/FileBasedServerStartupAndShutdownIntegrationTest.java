package com.netflix.eureka2.integration.startup;

import com.netflix.eureka2.server.EurekaWriteServer;
import org.junit.Test;

/**
 * Since this test relies on Archaius which has some global settings, we can't have different tests for both read
 * and write servers under the same JVM. As the tests are testing startup and shutdown behaviour that is common to
 * both (within AbstractEurekaServer), we just pick one of the two server types (write servers) to test this.
 *
 * @author David Liu
 */
public class FileBasedServerStartupAndShutdownIntegrationTest extends WriteServerStartupAndShutdownIntegrationTest {

    @Test(timeout = 60000)
    public void testStartsWithFileBasedConfiguration() throws Exception {
        injectConfigurationValuesViaSystemProperties(SERVER_NAME);
        EurekaWriteServer server = new EurekaWriteServer(SERVER_NAME);
        executeAndVerifyLifecycle(server);
        clearConfigurationValuesViaSystemProperties();
    }

}
