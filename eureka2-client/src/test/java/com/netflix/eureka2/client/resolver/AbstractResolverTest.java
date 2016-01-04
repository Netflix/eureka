package com.netflix.eureka2.client.resolver;

import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import com.netflix.eureka2.model.Server;
import rx.functions.Action0;

import static org.junit.Assert.fail;

/**
 * @author Tomasz Bak
 */
public abstract class AbstractResolverTest {

    protected static final Server SERVER_A = new Server("hostA", 123);
    protected static final Server SERVER_B = new Server("hostB", 124);
    protected static final Server SERVER_C = new Server("hostC", 125);

    protected Server takeNext(ServerResolver resolver) {
        try {
            ExtTestSubscriber<Server> testSubscriber = new ExtTestSubscriber<>();
            resolver.resolve().subscribe(testSubscriber);
            Server next = testSubscriber.takeNext(30, TimeUnit.SECONDS);
            testSubscriber.assertOnCompleted();
            return next;
        } catch (Exception e) {
            fail("Should not throw an exception: " + e);
            return null;
        }
    }

    protected Server takeNextWithEmits(ServerResolver resolver, Action0 emits) {
        try {
            ExtTestSubscriber<Server> testSubscriber = new ExtTestSubscriber<>();
            resolver.resolve().subscribe(testSubscriber);
            emits.call();  // do any emits inbetween the subscribe and the takeNext
            Server next = testSubscriber.takeNext(30, TimeUnit.SECONDS);
            testSubscriber.assertOnCompleted();
            return next;
        } catch (Exception e) {
            fail("Should not throw an exception: " + e);
            return null;
        }
    }
}
