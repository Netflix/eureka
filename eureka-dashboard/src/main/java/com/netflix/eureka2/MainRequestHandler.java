package com.netflix.eureka2;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;

import com.netflix.eureka2.config.EurekaDashboardConfig;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.reactivex.netty.protocol.http.server.HttpError;
import io.reactivex.netty.protocol.http.server.HttpServerRequest;
import io.reactivex.netty.protocol.http.server.HttpServerResponse;
import io.reactivex.netty.protocol.http.server.RequestHandler;
import io.reactivex.netty.protocol.http.server.file.ClassPathFileRequestHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import rx.Observable;

import static io.netty.handler.codec.http.HttpResponseStatus.*;

public class MainRequestHandler implements RequestHandler<ByteBuf, ByteBuf> {

    private static final Logger logger = LoggerFactory.getLogger(MainRequestHandler.class);
    public static final String REQ_PATH_GETCONFIG = "/getconfig";

    private final StaticFileHandler staticFileHandler;
    private EurekaDashboardConfig config;

    public static class StaticFileHandler extends ClassPathFileRequestHandler {
        public StaticFileHandler() {
            super("");
        }

        public boolean isStaticResource(String requestPath) {
            return requestPath != null &&
                    (requestPath.endsWith(".js") ||
                            requestPath.endsWith(".css") ||
                            requestPath.endsWith(".html") ||
                            requestPath.endsWith(".png") ||
                            requestPath.endsWith(".gif") ||
                            requestPath.endsWith(".map") ||
                            requestPath.endsWith(".ttf") ||
                            requestPath.endsWith(".woff") ||
                            requestPath.endsWith(".svg") ||
                            requestPath.endsWith(".jpg"));
        }

        /**
         * FIXME - this hack is needed to read from jar
         */
        @Override
        public Observable<Void> handle(final HttpServerRequest<ByteBuf> request, final HttpServerResponse<ByteBuf> response) {
            try {
                return super.handle(request, response);
            } catch (Exception ex) {
                // Ignore
            }

            URI fileUri = resolveUri(request.getPath());
            if(fileUri == null) {
                return Observable.error(new HttpError(NOT_FOUND));
            }
            response.setStatus(OK);
            try {
                byte[] byteBuf = new byte[4096];
                int len;
                InputStream is = new BufferedInputStream(fileUri.toURL().openConnection().getInputStream());
                try {
                    while ((len = is.read(byteBuf)) != -1) {
                        response.write(response.getAllocator().buffer().writeBytes(byteBuf, 0, len));
                    }
                } finally {
                    is.close();
                }
            } catch (IOException e) {
                return Observable.error(e);
            }
            return response.close();
        }

        @Override
        protected URI resolveUri(String resourcePath) {
            if (resourcePath.startsWith("/")) {
                resourcePath = '.' + resourcePath;
            } else if (!resourcePath.startsWith(".")) {
                resourcePath = "./" + resourcePath;
            }
            try {
                URL url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
                if (url == null) {
                    resourcePath = resourcePath.substring(2); // get rid of './' and try again
                    url = Thread.currentThread().getContextClassLoader().getResource(resourcePath);
                    if (url == null) {
                        logger.debug("Resource '{}' not found ", resourcePath);
                        return null;
                    }
                }
                return url.toURI();
            } catch (URISyntaxException e) {
                logger.debug("Error resolving uri for '{}'", resourcePath);
                return null;
            }
        }
    }

    public MainRequestHandler(EurekaDashboardConfig config) {
        this.config = config;
        staticFileHandler = new StaticFileHandler();
    }

    @Override
    public Observable<Void> handle(HttpServerRequest<ByteBuf> request, HttpServerResponse<ByteBuf> response) {
        final String reqPath = request.getPath();
        if (reqPath.equals(REQ_PATH_GETCONFIG)) {
            return sendConfig(response);
        } else if (staticFileHandler.isStaticResource(reqPath)) {
            return staticFileHandler.handle(request, response);
        } else {
            response.setStatus(HttpResponseStatus.BAD_REQUEST);
            return response.writeStringAndFlush("Not implemented yet\n");
        }
    }

    private Observable<Void> sendConfig(HttpServerResponse<ByteBuf> response) {
        response.getHeaders().set("Content-Type", "application/json");
        final String resp = "{ \"wsport\" : " + "\"" + config.getWebSocketPort() + "\" }";
        ByteBuf output = response.getAllocator().buffer().writeBytes(resp.getBytes());
        response.setStatus(HttpResponseStatus.OK);
        return response.writeAndFlush(output);
    }


}
