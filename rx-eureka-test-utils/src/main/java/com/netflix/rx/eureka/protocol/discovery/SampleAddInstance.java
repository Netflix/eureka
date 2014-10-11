package com.netflix.rx.eureka.protocol.discovery;

import com.netflix.rx.eureka.registry.InstanceInfo;
import com.netflix.rx.eureka.registry.SampleInstanceInfo;
import rx.Observable;

import java.util.ArrayList;
import java.util.Collection;

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
        return new AddInstance(seed);
    }

    public static Observable<AddInstance> newMessages(SampleAddInstance app, int n) {
        Collection<AddInstance> result = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            result.add(app.newMessage());
        }
        return Observable.from(result);
    }

}
