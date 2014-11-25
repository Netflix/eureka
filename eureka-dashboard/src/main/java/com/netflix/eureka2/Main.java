package com.netflix.eureka2;

import com.google.inject.Guice;
import com.google.inject.Injector;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws InterruptedException {

        final ExecutorService executor = Executors.newFixedThreadPool(2);

        final Injector injector = Guice.createInjector();
        final MainAppServer staticFileServer = injector.getInstance(MainAppServer.class);
        final WebSocketServer webSocketServer = injector.getInstance(WebSocketServer.class);

        try {
            executor.submit(new Runnable() {
                @Override
                public void run() {
                    staticFileServer.createServer().startAndWait();
                }
            });

            executor.submit(new Runnable() {
                @Override
                public void run() {
                    webSocketServer.createServer().startAndWait();
                }
            });
        } finally {
            executor.shutdown();
        }

    }
}
