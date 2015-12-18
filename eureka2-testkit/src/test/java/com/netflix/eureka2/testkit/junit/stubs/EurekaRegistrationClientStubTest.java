package com.netflix.eureka2.testkit.junit.stubs;

import com.netflix.eureka2.client.EurekaRegistrationClient.RegistrationStatus;
import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import com.netflix.eureka2.testkit.internal.rx.ExtTestSubscriber;
import org.junit.Test;
import rx.Observable;
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

        Observable<RegistrationStatus> registrationObservable = clientStub.register(registrarSubject);

        ExtTestSubscriber<RegistrationStatus> registrationSubscriber = new ExtTestSubscriber<>();
        ExtTestSubscriber<RegistrationStatus> initSubscriber = new ExtTestSubscriber<>();
        registrationObservable.subscribe(registrationSubscriber);
        registrationObservable.take(1).subscribe(initSubscriber);

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