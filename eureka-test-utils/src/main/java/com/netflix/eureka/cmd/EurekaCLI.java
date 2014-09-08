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

import com.netflix.eureka.interests.Interest;
import com.netflix.eureka.utils.Sets;
import com.netflix.eureka.client.EurekaClient;
import com.netflix.eureka.client.EurekaClientImpl;
import com.netflix.eureka.client.bootstrap.StaticServerResolver;
import com.netflix.eureka.client.service.EurekaServiceImpl;
import com.netflix.eureka.client.transport.TransportClient;
import com.netflix.eureka.client.transport.TransportClients;
import com.netflix.eureka.interests.ChangeNotification;
import com.netflix.eureka.interests.Interests;
import com.netflix.eureka.registry.Delta;
import com.netflix.eureka.registry.Delta.Builder;
import com.netflix.eureka.registry.InstanceInfo;
import com.netflix.eureka.registry.InstanceInfoField;
import com.netflix.eureka.registry.InstanceInfoField.Name;
import com.netflix.eureka.registry.SampleInstanceInfo;
import com.netflix.eureka.service.EurekaService;
import com.netflix.eureka.transport.EurekaTransports.Codec;
import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;
import org.eclipse.jetty.util.ConcurrentHashSet;
import rx.Subscriber;

/**
 * Simple command line Eureka client interface.
 *
 * @author Tomasz Bak
 */
public class EurekaCLI {

    private enum Status {NotStarted, Initiated, Streaming, Complete, Failed}

    private final ConsoleReader consoleReader;

    private AtomicInteger idGenerator = new AtomicInteger(1);
    private volatile InstanceInfo lastInstanceInfo;
    private EurekaClient eurekaClient;
    private ConcurrentHashSet<InstanceInfo> interestData = new ConcurrentHashSet<>();
    private Status registrationStatus = Status.NotStarted;
    private Status registryFetchStatus = Status.NotStarted;

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
                    System.out.println("Terminating...");
                    return;
                }
                try {
                    if ("help".equals(cmd) && expect(cmd, 0, args)) {
                        runHelp();
                    } else if ("connect".equals(cmd) && expect(cmd, 3, args)) {
                        runConnect(args);
                    } else if ("register".equals(cmd) && expect(cmd, 0, args)) {
                        runRegister();
                    } else if ("update".equals(cmd) && expect(cmd, 2, args)) {
                        runUpdate(args);
                    } else if ("close".equals(cmd) && expect(cmd, 0, args)) {
                        runClose();
                    } else if ("status".equals(cmd) && expect(cmd, 0, args)) {
                        runStatus();
                    } else if ("interestAll".equals(cmd) && expect(cmd, 0, args)) {
                        listenForRegistry(Interests.forFullRegistry());
                    } else if ("interest".equals(cmd) && atLeast(cmd, 1, args)) {
                        listenForInterest(args);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
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

    private boolean atLeast(String cmd, int atLeastArgs, String[] args) {
        if (args.length < atLeastArgs) {
            System.err.println(String.format("ERROR: command %s expects at least %d arguments", cmd, atLeastArgs));
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
        System.out.println("  update <field> <value>                               update own registry entry ");
        System.out.println("  status                                               print status summary      ");
        System.out.println("  interestAll                                          start intrest subscription for all");
        System.out.println("  interest <vipName>                                   start interest subscription for given vips");
    }

    private void runConnect(String[] args) {
        String host = args[0];
        int registrationPort = Integer.parseInt(args[1]);
        int discoveryPort = Integer.parseInt(args[2]);

        InetSocketAddress writeHost = new InetSocketAddress(host, registrationPort);
        InetSocketAddress readHost = new InetSocketAddress(host, discoveryPort);

        TransportClient writeClient =
                TransportClients.newTcpRegistrationClient(new StaticServerResolver<>(writeHost), Codec.Json);

        TransportClient readClient =
                TransportClients.newTcpDiscoveryClient(new StaticServerResolver<>(readHost), Codec.Json);

        EurekaService eurekaService = EurekaServiceImpl.forReadAndWriteServer(readClient, writeClient);

        eurekaClient = new EurekaClientImpl(eurekaService);
    }

    private void runRegister() {
        if (!isConnected() || !expectedRegistrationStatus(Status.NotStarted, Status.Failed)) {
            return;
        }

        registrationStatus = Status.Initiated;
        lastInstanceInfo = SampleInstanceInfo.CliServer.builder()
//                .withId(Integer.toString(idGenerator.getAndIncrement()))
                .build();
        eurekaClient.register(lastInstanceInfo)
                .subscribe(new Subscriber<Void>() {
                    @Override
                    public void onCompleted() {
                        System.out.println("Successfuly registered with Eureka server");
                        registrationStatus = Status.Complete;
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("ERROR: Registration failed.");
                        e.printStackTrace();
                        registrationStatus = Status.Failed;
                    }

                    @Override
                    public void onNext(Void aVoid) {
                        // No op
                    }
                });
    }

    private void runUpdate(String[] args) {
        if (!isConnected() || !expectedRegistrationStatus(Status.Complete)) {
            return;
        }

        Name name = Name.valueOf(args[0]);
        InstanceInfoField<Object> field = InstanceInfoField.forName(name);

        Object value;
        if (field.getValueType().equals(Integer.class)) {
            value = Integer.parseInt(args[1]);
        } else if (field.getValueType().equals(InstanceInfo.Status.class)) {
            value = InstanceInfo.Status.valueOf(args[1]);
        } else {
            value = args[1];
        }

        Delta<?> delta = new Builder()
                .withId(lastInstanceInfo.getId())
                .withVersion(lastInstanceInfo.getVersion())
                .withDelta(field, value)
                .build();
        lastInstanceInfo = lastInstanceInfo.applyDelta(delta);

        eurekaClient.update(lastInstanceInfo).subscribe(new Subscriber<Void>() {
            @Override
            public void onCompleted() {
                System.out.println("Successfuly updated registry information.");
            }

            @Override
            public void onError(Throwable e) {
                System.out.println("ERROR: Registration update failed.");
                e.printStackTrace();
                registrationStatus = Status.Failed;
            }

            @Override
            public void onNext(Void aVoid) {
                // No op
            }
        });
    }

    private boolean isConnected() {
        if (eurekaClient == null) {
            System.out.println("ERROR: connect first to Eureka server");
            return false;
        }
        return true;
    }

    private boolean expectedRegistrationStatus(Status... statusArray) {
        if (Sets.asSet(statusArray).contains(registrationStatus)) {
            return true;
        }
        switch (registrationStatus) {
            case NotStarted:
                System.out.println("ERROR: Registration not started yet.");
                break;
            case Initiated:
                System.out.println("ERROR: Registration already in progress.");
                break;
            case Complete:
                System.out.println("ERROR: Registration already done.");
                break;
            case Failed:
                System.out.println("ERROR: Previous registration failed.");
                break;
        }
        return false;
    }

    private void listenForInterest(String[] args) {

        listenForRegistry(Interests.forVips(args));
    }

    private void listenForRegistry(final Interest<InstanceInfo> interest) {
        if (eurekaClient == null) {
            System.out.println("Not connected");
            return;
        }

        switch (registryFetchStatus) {
            case NotStarted:
                break;
            case Initiated:
            case Streaming:
                System.out.println("ERROR: Registry fetch already in progress.");
                return;
            case Complete:
                System.out.println("ERROR: Registry fetch already done.");
                return;
            case Failed:
                System.out.println("ERROR: Previous registry fetch failed, so proceeding with this request.");
                break;
        }

        final AtomicInteger addCounter = new AtomicInteger();
        final AtomicInteger deleteCounter = new AtomicInteger();
        final AtomicInteger updateCounter = new AtomicInteger();

        registryFetchStatus = Status.Initiated;
        eurekaClient.forInterest(interest)
                .subscribe(new Subscriber<ChangeNotification<InstanceInfo>>() {
                    @Override
                    public void onCompleted() {
                        registryFetchStatus = Status.Complete;
                    }

                    @Override
                    public void onError(Throwable e) {
                        System.out.println("ERROR: Fetch registry failed.");
                        e.printStackTrace();
                        registryFetchStatus = Status.Failed;
                    }

                    @Override
                    public void onNext(ChangeNotification<InstanceInfo> notification) {
                        registryFetchStatus = Status.Streaming;
                        switch (notification.getKind()) {
                            case Add:
                                interestData.add(notification.getData());
                                System.out.println("Instance added (" + addCounter.incrementAndGet() + "): " + notification.getData());
                                break;
                            case Delete:
                                interestData.remove(notification.getData());
                                System.out.println("Instance deleted (" + deleteCounter.incrementAndGet() + "): " + notification.getData());
                                break;
                            case Modify:
                                interestData.add(notification.getData());
                                System.out.println("Instance updated (" + updateCounter.incrementAndGet() + "): " + notification.getData());
                                break;
                        }
                    }
                });
    }

    private void runClose() {
        if (eurekaClient != null) {
            System.out.println("Bye!");
            eurekaClient.close();
            eurekaClient = null;
            registrationStatus = Status.NotStarted;
            registryFetchStatus = Status.NotStarted;
        }
    }

    private void runStatus() {
        System.out.println("Status report");
        System.out.println("=============");
        if (eurekaClient == null) {
            System.out.println("Connection status: disconnected");
        } else {
            System.out.println("Connection status: connected");
            switch (registrationStatus) {
                case NotStarted:
                    System.out.println("Registration status: unregistered");
                    break;
                case Initiated:
                    System.out.println("Registration status: Initiated but not completed.");
                    break;
                case Complete:
                    System.out.println("Registration status: registered");
                    break;
                case Failed:
                    System.out.println("Registration status: failed");
                    break;
            }
            switch (registryFetchStatus) {
                case NotStarted:
                    System.out.println("Registry fetch status: not initiated");
                    break;
                case Initiated:
                    System.out.println("Registry fetch status: Initiated but not completed.");
                    break;
                case Streaming:
                    System.out.println("Registry fetch status: streaming updates from server");
                    break;
                case Complete:
                    System.out.println("Registry fetch status: finished");
                    break;
                case Failed:
                    System.out.println("Registry fetch status: failed");
                    break;
            }
        }

        System.out.println(String.format("Local registry: %d instances", interestData.size()));
    }

    public static void main(String[] args) throws IOException {
        new EurekaCLI().readExecutePrintLoop();
    }
}
