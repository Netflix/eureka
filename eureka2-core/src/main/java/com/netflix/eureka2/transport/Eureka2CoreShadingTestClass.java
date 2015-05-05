package com.netflix.eureka2.transport;

import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.client.ClientBuilder;

/**
 * A class that exposes RxNetty constructs to verify shading in eureka2-client-shaded
 *
 * @author David Liu
 */
public class Eureka2CoreShadingTestClass {
    public static final ClientBuilder<ByteBuf, ByteBuf> TEST_CLIENT_BUILDER = RxNetty.newTcpClientBuilder("localhost", 0);
}
