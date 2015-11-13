package com.netflix.eureka2.server.service.selfinfo;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.netflix.eureka2.model.InstanceModel;
import com.netflix.eureka2.model.StdModelsInjector;
import com.netflix.eureka2.model.instance.InstanceInfo;
import org.junit.Assert;
import org.junit.Test;
import rx.Observable;

/**
 * @author David Liu
 */
public class SelfInfoResolverChainTest {

    static {
        StdModelsInjector.injectStdModels();
    }

    @Test(timeout = 30000)
    public void testChaining() throws Exception {
        ChainableSelfInfoResolver one = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withId("id").withApp("appName"))
        );

        ChainableSelfInfoResolver two = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.STARTING))
        );

        ChainableSelfInfoResolver three = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.UP))
        );

        SelfInfoResolverChain chain = new SelfInfoResolverChain(one, two, three);

        List<InstanceInfo> result = chain.resolve().toList().toBlocking().firstOrDefault(null);
        Assert.assertEquals(1, result.size());
        InstanceInfo info = result.get(0);
        Assert.assertEquals("id", info.getId());
        Assert.assertEquals("appName", info.getApp());
        Assert.assertEquals(InstanceInfo.Status.UP, info.getStatus());
    }

    @Test(timeout = 30000)
    public void testLastObservableCompletesBeforeMiddle() {
        ChainableSelfInfoResolver one = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withId("id").withApp("appName"))
        );

        ChainableSelfInfoResolver two = new ChainableSelfInfoResolver(
                Observable.from(
                        Arrays.asList(
                                InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.STARTING),
                                InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.UP),
                                InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.DOWN)
                        )
                ).delay(500, TimeUnit.MILLISECONDS)  // delay this stream a bit so that we definitely get 3 distinct emits
        );

        ChainableSelfInfoResolver three = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withVipAddress("vip"))
        );

        SelfInfoResolverChain chain = new SelfInfoResolverChain(one, two, three);

        List<InstanceInfo> result = chain.resolve().toList().toBlocking().firstOrDefault(null);

        System.out.println(result);
        Assert.assertEquals(3, result.size());

        InstanceInfo info0 = result.get(0);
        Assert.assertEquals("id", info0.getId());
        Assert.assertEquals("appName", info0.getApp());
        Assert.assertEquals(InstanceInfo.Status.STARTING, info0.getStatus());
        Assert.assertEquals("vip", info0.getVipAddress());

        InstanceInfo info1 = result.get(1);
        Assert.assertEquals("id", info1.getId());
        Assert.assertEquals("appName", info1.getApp());
        Assert.assertEquals(InstanceInfo.Status.UP, info1.getStatus());
        Assert.assertEquals("vip", info1.getVipAddress());

        InstanceInfo info2 = result.get(2);
        Assert.assertEquals("id", info2.getId());
        Assert.assertEquals("appName", info2.getApp());
        Assert.assertEquals(InstanceInfo.Status.DOWN, info2.getStatus());
        Assert.assertEquals("vip", info2.getVipAddress());
    }

    @Test(timeout = 30000)
    public void testMiddleObservableCompletesBeforeLast() throws Exception {
        ChainableSelfInfoResolver one = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withId("id").withApp("appName"))
        );

        ChainableSelfInfoResolver two = new ChainableSelfInfoResolver(
                Observable.just(InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.STARTING))
        );

        ChainableSelfInfoResolver three = new ChainableSelfInfoResolver(
                Observable.from(
                        Arrays.asList(
                                InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.UP),
                                InstanceModel.getDefaultModel().newInstanceInfo().withStatus(InstanceInfo.Status.DOWN)
                        )
                ).delay(500, TimeUnit.MILLISECONDS)  // delay this stream a bit so that we definitely get 3 distinct emits
        );

        SelfInfoResolverChain chain = new SelfInfoResolverChain(one, two, three);

        List<InstanceInfo> result = chain.resolve().toList().toBlocking().firstOrDefault(null);
        Assert.assertEquals(2, result.size());

        InstanceInfo info0 = result.get(0);
        Assert.assertEquals("id", info0.getId());
        Assert.assertEquals("appName", info0.getApp());
        Assert.assertEquals(InstanceInfo.Status.UP, info0.getStatus());

        InstanceInfo info1 = result.get(1);
        Assert.assertEquals("id", info1.getId());
        Assert.assertEquals("appName", info1.getApp());
        Assert.assertEquals(InstanceInfo.Status.DOWN, info1.getStatus());
    }
}
