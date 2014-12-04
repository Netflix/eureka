package com.netflix.eureka2;

import com.netflix.eureka2.registry.InstanceInfo;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.reactivex.netty.channel.ObservableConnection;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertTrue;

@RunWith(MockitoJUnitRunner.class)
public class RegistryStreamTest extends RegistryTestBase {

    @Test
    public void subscribeToStream() throws InterruptedException {
        RegistryCache registryCache = new RegistryCache(dashboardEurekaClientBuilder);
        RegistryStream registryStream = new RegistryStream(registryCache);

        final CountDownLatch latch = new CountDownLatch(1);

        registryStream.subscribe(new RegistryStream.RegistryStreamCallback() {
            @Override
            public boolean streamReceived(List<RegistryStream.RegistryItem> registryItems) {
                assertTrue(registryItems != null);
                assertTrue(registryItems.size() == 4);
                latch.countDown();
                return true;
            }
        });

        assertTrue(latch.await(5, TimeUnit.SECONDS));
    }

}
