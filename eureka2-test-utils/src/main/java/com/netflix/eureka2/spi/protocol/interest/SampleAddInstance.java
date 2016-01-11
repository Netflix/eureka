package com.netflix.eureka2.spi.protocol.interest;

import java.util.ArrayList;
import java.util.Collection;

import com.netflix.eureka2.model.instance.InstanceInfo;
import com.netflix.eureka2.spi.model.TransportModel;
import com.netflix.eureka2.spi.model.transport.notification.AddInstance;
import com.netflix.eureka2.testkit.data.builder.SampleInstanceInfo;
import rx.Observable;

/**
 * @author David Liu
 */
public enum SampleAddInstance {

    ZuulAdd() {
        @Override
        public AddInstance newMessage() {
            return newMessage(SampleInstanceInfo.ZuulServer.build());
        }
    },
    DiscoveryAdd() {
        @Override
        public AddInstance newMessage() {
            return newMessage(SampleInstanceInfo.DiscoveryServer.build());
        }
    };

    public abstract AddInstance newMessage();

    public AddInstance newMessage(InstanceInfo seed) {
        return TransportModel.getDefaultModel().newAddInstance(seed);
    }

    public static Observable<AddInstance> newMessages(SampleAddInstance app, int n) {
        Collection<AddInstance> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(app.newMessage());
        }
        return Observable.from(result);
    }
}
