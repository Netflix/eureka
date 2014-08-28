/*
 * Copyright 2014 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.eureka.cmd;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicInteger;

import com.netflix.eureka.client.BootstrapResolver;
import com.netflix.eureka.client.bootstrap.StaticBootstrapResolver;
import com.netflix.eureka.client.service.EurekaServiceImpl;
import com.netflix.eureka.client.transport.discovery.DiscoveryClientProvider;
import com.netflix.eureka.client.transport.registration.RegistrationClientProvider;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.service.RegistrationChannel;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;
import rx.Subscriber;

import static java.util.Collections.*;

/**
 * Simple command line Eureka client interface.
 *
 * @author Tomasz Bak
 */
public class EurekaCLI {

    private final ConsoleReader consoleReader;

    private EurekaServiceImpl eurekaService;
    private volatile RegistrationChannel registrationChannel;
    private AtomicInteger idGenerator = new AtomicInteger(1);

    public EurekaCLI() throws IOException {
        Terminal terminal = TerminalFactory.create();
        terminal.setEchoEnabled(false);
        consoleReader = new ConsoleReader(System.in, System.out, terminal);
    }

    public void readExecutePrintLoop() throws IOException {
        System.out.println("Eureka 2.0 Command Line Client");
        while (true) {
            String line = consoleReader.readLine("> ");
            if (line != null && !(line = line.trim()).isEmpty()) {
                String[] parts = line.split("\\s+");
                String cmd = parts[0];
                String[] args = Arrays.copyOfRange(parts, 1, parts.length);
                if ("quit".equals(cmd)) {
                    System.out.println("Terminatting...");
                    return;
                } else if ("help".equals(cmd) && expect(cmd, 0, args)) {
                    runHelp();
                } else if ("connect".equals(cmd) && expect(cmd, 3, args)) {
                    runConnect(args);
                } else if ("register".equals(cmd) && expect(cmd, 0, args)) {
                    runRegister();
                } else if ("close".equals(cmd) && expect(cmd, 0, args)) {
                    runClose();
                } else if ("status".equals(cmd) && expect(cmd, 0, args)) {
                    runStatus();
                }
            }
        }
    }

    private boolean expect(String cmd, int expectedArgs, String[] args) {
        if (args.length != expectedArgs) {
            System.err.println(String.format("ERROR: command %s expects %d arguments", cmd, expectedArgs));
            return false;
        }
        return true;
    }

    private void runHelp() {
        System.out.println("Available commands:");
        System.out.println("  close                                                close all channels        ");
        System.out.println("  connect <host> <registration_port> <discovery_port>  ports: 7002, 7003         ");
        System.out.println("  help                                                 print this help           ");
        System.out.println("  quit                                                 exit                      ");
        System.out.println("  register                                             register with server      ");
        System.out.println("  status                                               print status summary      ");
    }

    private void runConnect(String[] args) {
        String host = args[0];
        int registrationPort = Integer.parseInt(args[1]);
        int discoveryPort = Integer.parseInt(args[2]);

        BootstrapResolver<InetSocketAddress> registratonBootstrap =
                new StaticBootstrapResolver<InetSocketAddress>(singletonList(new InetSocketAddress(host, registrationPort)));
        RegistrationClientProvider<InetSocketAddress> registrationClientProvider =
                RegistrationClientProvider.tcpClientProvider(registratonBootstrap, Codec.Json);

        BootstrapResolver<InetSocketAddress> discoveryBootstrap =
                new StaticBootstrapResolver<InetSocketAddress>(singletonList(new InetSocketAddress(host, discoveryPort)));
        DiscoveryClientProvider<InetSocketAddress> discoveryClientProvider =
                DiscoveryClientProvider.tcpClientProvider(discoveryBootstrap, Codec.Json);

        eurekaService = new EurekaServiceImpl(discoveryClientProvider, registrationClientProvider);
    }

    private void runRegister() {
        if (eurekaService == null) {
            System.out.println("ERROR: connect first to Eureka server");
            return;
        }
        registrationChannel = eurekaService.newRegistrationChannel();
        registrationChannel.asLifecycleObservable().subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                System.out.println("Registration channel closed");
                registrationChannel = null;
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("Registration channel terminated with an error");
                e.printStackTrace();
                registrationChannel = null;
            }

            @Override
            public void onNext(Void aVoid) {
            }
        });
        registrationChannel.register(SampleInstanceInfo.ZuulServer.builder()
                .withId(Integer.toString(idGenerator.getAndIncrement())).build());
    }

    private void runClose() {
        if (registrationChannel != null) {
            System.out.println("Closing registration channel...");
            registrationChannel.close();
            registrationChannel = null;
        }
        if (eurekaService != null) {
            // TODO: lifecycle for eureka service?
        }
    }

    private void runStatus() {
        System.out.println("Status report");
        System.out.println("=============");
        if (eurekaService == null) {
            System.out.println("Connection status: disconnected");
        } else {
            System.out.println("Connection status: connected");
            if (registrationChannel == null) {
                System.out.println("Registration status: unregistered");
            } else {
                System.out.println("Registration status: registered");
            }
        }
    }

    public static void main(String[] args) throws IOException {
        new EurekaCLI().readExecutePrintLoop();
    }
}
