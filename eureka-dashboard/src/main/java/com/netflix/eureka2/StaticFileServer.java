package com.netflix.eureka2;


import io.netty.buffer.ByteBuf;
import io.reactivex.netty.RxNetty;
import io.reactivex.netty.protocol.http.server.HttpServer;
import io.reactivex.netty.protocol.http.server.RequestHandlerWithErrorMapper;
import io.reactivex.netty.protocol.http.server.file.ClassPathFileRequestHandler;
import io.reactivex.netty.protocol.http.server.file.FileErrorResponseMapper;

import com.google.inject.Singleton;

@Singleton
public class StaticFileServer {
    static final int DEFAULT_PORT = 7001;

    private final int port;

    public StaticFileServer() {
        this.port = DEFAULT_PORT;
    }

    public static class StaticFileHandler extends ClassPathFileRequestHandler {
        public StaticFileHandler() {
            super(".");
        }
    }

    public HttpServer<ByteBuf, ByteBuf> createServer() {
        HttpServer<ByteBuf, ByteBuf> server = RxNetty.createHttpServer(port,
                RequestHandlerWithErrorMapper.from(
                        new StaticFileHandler(),
                        new FileErrorResponseMapper()));
        System.out.println("HTTP file server started on " + server.getServerPort());
        return server;
    }

    public static void main(String[] args) {
        new StaticFileServer().createServer().startAndWait();
    }
}
