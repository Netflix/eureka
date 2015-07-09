package com.netflix.eureka2.testkit.junit.stubs;

import com.netflix.eureka2.client.registration.RegistrationObservable;
import com.netflix.eureka2.registry.instance.InstanceInfo;
import com.netflix.eureka2.rx.ExtTestSubscriber;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import org.junit.Test;
import rx.subjects.PublishSubject;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.junit.Assert.assertThat;

/**
 * @author Tomasz Bak
 */
public class EurekaRegistrationClientStubTest {

    @Test
    public void testStateRecording() throws Exception {
        EurekaRegistrationClientStub clientStub = new EurekaRegistrationClientStub();
        PublishSubject<InstanceInfo> registrarSubject = PublishSubject.create();

        RegistrationObservable registrationObservable = clientStub.register(registrarSubject);

        ExtTestSubscriber<Void> registrationSubscriber = new ExtTestSubscriber<>();
        ExtTestSubscriber<Void> initSubscriber = new ExtTestSubscriber<>();
        registrationObservable.subscribe(registrationSubscriber);
        registrationObservable.initialRegistrationResult().subscribe(initSubscriber);

        assertThat(registrationSubscriber.isUnsubscribed(), is(false));
        assertThat(initSubscriber.isUnsubscribed(), is(false));

        // Trigger first registration
        InstanceInfo instanceInfo = SampleInstanceInfo.WebServer.build();
        registrarSubject.onNext(instanceInfo);

        assertThat(registrationSubscriber.isUnsubscribed(), is(false));
        assertThat(initSubscriber.isUnsubscribed(), is(true));

        assertThat(clientStub.getLastRegistrationUpdate(), is(equalTo(instanceInfo)));
    }
}