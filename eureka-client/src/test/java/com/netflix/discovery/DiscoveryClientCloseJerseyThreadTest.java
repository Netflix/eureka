package com.netflix.discovery;

import java.util.Set;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class DiscoveryClientCloseJerseyThreadTest extends AbstractDiscoveryClientTester {

    private static final String THREAD_NAME = "Eureka-JerseyClient-Conn-Cleaner";

    @Test
    public void testThreadCount() throws InterruptedException {
        assertThat(containsJerseyThread(), equalTo(true));
        client.shutdown();
        // Give up control for cleaner thread to die
        Thread.sleep(5);
        assertThat(containsJerseyThread(), equalTo(false));
    }

    private boolean containsJerseyThread() {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread t : threads) {
            if (t.getName().contains(THREAD_NAME)) {
                return true;
            }
        }
        return false;
    }
}
