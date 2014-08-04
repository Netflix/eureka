package com.netflix.eureka.interests;

import com.netflix.eureka.registry.EurekaRegistry;
import com.netflix.eureka.registry.EurekaRegistryImpl;
import com.netflix.eureka.registry.InstanceInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExternalResource;
import rx.functions.Action1;

/**
 * @author Nitesh Kant
 */
public class RegistryIndexTest {

    private EurekaRegistry registry;

    @Rule
    private final ExternalResource registryResource = new ExternalResource() {

        @Override
        protected void before() throws Throwable {
            registry = new EurekaRegistryImpl();
        }

        @Override
        protected void after() {
            //shutdown registry
        }
    };

    @Test
    public void testBasicIndex() throws Exception {

        registry.forInterest(Interests.forFullRegistry())
                .subscribe(new Action1<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void call(ChangeNotification<InstanceInfo> notification) {
                        System.out.println("Notification: " + notification);
                    }
                });
    }
}
