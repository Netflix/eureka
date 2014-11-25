package com.netflix.eureka2;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.http.server.file.ClassPathFileRequestHandler;
import rx.Observable;

public class MainRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private final StaticFileHandler staticFileHandler;

    public static class StaticFileHandler extends ClassPathFileRequestHandler {
        public StaticFileHandler() {
            super(".");
        }

        public boolean isStaticResource(String requestPath) {
            return requestPath != null &&
                    (requestPath.endsWith(".js") ||
                            requestPath.endsWith(".css") ||
                            requestPath.endsWith(".html") ||
                            requestPath.endsWith(".png") ||
                            requestPath.endsWith(".gif") ||
                            requestPath.endsWith(".map") ||
                            requestPath.endsWith(".jpg") ||
                            requestPath.endsWith(".ttf") ||
                            requestPath.endsWith(".woff") ||
                            requestPath.endsWith(".svg") ||
                            requestPath.endsWith(".jpg"));
        }
    }

    public MainRequestHandler() {
        staticFileHandler = new StaticFileHandler();
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        final String reqPath = request.getPath();
        if (reqPath.equals("/dump")) {
            response.getHeaders().set("Content-Type", "application/json");
            final String resp = "{ \"name\" : \"amit\", \"age\" : 35 }";
            ByteBuf output = response.getAllocator().buffer().writeBytes(resp.getBytes());
            response.setStatus(HttpResponseStatus.OK);
            return response.writeAndFlush(output);
        } else if (staticFileHandler.isStaticResource(reqPath)) {
            return staticFileHandler.handle(request, response);
        } else {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return response.writeStringAndFlush("Not implemented yet\n");
        }
    }
}
