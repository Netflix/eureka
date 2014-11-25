package com.netflix.eureka2;


import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.RequestHandlerWithErrorMapper;
import io.reactivex.netty.protocol.http.server.file.FileErrorResponseMapper;

import com.google.inject.Singleton;

@Singleton
public class MainAppServer {
    static final int DEFAULT_PORT = 7001;

    private final int port;

    public MainAppServer() {
        this.port = DEFAULT_PORT;
    }

    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.createHttpServer(port,
                RequestHandlerWithErrorMapper.from(
                        new MainRequestHandler(),
                        new FileErrorResponseMapper()));
        System.out.println("HTTP file server started on " + server.getServerPort());
        return server;
    }

    public static void main(String[] args) {
        new MainAppServer().createServer().startAndWait();
    }
}
