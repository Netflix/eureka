package com.netflix.discovery;

import java.util.Set;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;

public class DiscoveryClientCloseJerseyThreadTest extends AbstractDiscoveryClientTester {

    private static final String JERSEY_THREAD_NAME = "Eureka-JerseyClient-Conn-Cleaner";
    private static final String APACHE_THREAD_NAME = "Apache-HttpClient-Conn-Cleaner";

    @Test
    public void testThreadCount() throws InterruptedException {
        assertThat(containsClientThread(), equalTo(true));
        client.shutdown();
        // Give up control for cleaner thread to die
        Thread.sleep(5);
        assertThat(containsClientThread(), equalTo(false));
    }

    private boolean containsClientThread() {
        Set<Thread> threads = Thread.getAllStackTraces().keySet();
        for (Thread t : threads) {
            if (t.getName().contains(JERSEY_THREAD_NAME) || t.getName().contains(APACHE_THREAD_NAME)) {
                return true;
            }
        }
        return false;
    }
}
