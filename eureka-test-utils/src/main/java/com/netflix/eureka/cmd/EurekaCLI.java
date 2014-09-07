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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.eureka.Sets;
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
import jline.console.history.History;
import jline.console.history.History.Entry;
import rx.Subscriber;

/**
 * Simple command line Eureka client interface.
 *
 * @author Tomasz Bak
 */
public class EurekaCLI {

    private static final File CONFIGURATION_FILE =
            System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home"), ".eurekarc");

    private static final File HISTORY_FILE =
            System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home"), ".eureka_history");

    private static final Pattern ALIAS_DEF_PATTERN = Pattern.compile("\\s*alias\\s+(\\w+)=(.*)");

    private enum Status {NotStarted, Initiated, Streaming, Complete, Failed}

    private final ConsoleReader consoleReader;
    private TreeMap<String, String> aliasMap = new TreeMap<>();

    private AtomicInteger idGenerator = new AtomicInteger(1);
    private volatile InstanceInfo lastInstanceInfo;
    private EurekaClient eurekaClient;
    private Status registrationStatus = Status.NotStarted;
    private Status registryFetchStatus = Status.NotStarted;

    public EurekaCLI() throws IOException {
        Terminal terminal = TerminalFactory.create();
        terminal.setEchoEnabled(false);
        consoleReader = new ConsoleReader(System.in, System.out, terminal);
        loadConfiguration();
        loadHistory();
    }

    private void loadConfiguration() {
        if (CONFIGURATION_FILE != null && CONFIGURATION_FILE.exists()) {
            try {
                LineNumberReader reader = new LineNumberReader(new FileReader(CONFIGURATION_FILE));
                try {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        Matcher matcher = ALIAS_DEF_PATTERN.matcher(line);
                        if (matcher.matches()) {
                            aliasMap.put(matcher.group(1), matcher.group(2));
                        }
                    }
                } finally {
                    reader.close();
                }
            } catch (IOException e) {
                System.err.println("ERROR: could not load configuration file from ~/.eureka_history");
            }
        }
    }

    private void loadHistory() {
        if (HISTORY_FILE != null && HISTORY_FILE.exists()) {
            try {
                LineNumberReader reader = new LineNumberReader(new FileReader(HISTORY_FILE));
                try {
                    History history = consoleReader.getHistory();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        history.add(line);
                    }
                } finally {
                    reader.close();
                }
            } catch (IOException e) {
                System.err.println("ERROR: could not load history file from ~/.eureka_history");
            }
        }
    }

    private void saveHistory() {
        if (HISTORY_FILE != null) {
            if (HISTORY_FILE.exists()) {
                HISTORY_FILE.delete();
            }
            try {
                FileWriter writer = new FileWriter(HISTORY_FILE);
                ListIterator<Entry> iterator = consoleReader.getHistory().entries();
                try {
                    while (iterator.hasNext()) {
                        writer.write(iterator.next().value().toString());
                        writer.write('\n');
                    }
                } finally {
                    writer.close();
                }
            } catch (IOException e) {
                System.err.println("ERROR: could not save history file into ~/.eureka_history");
            }
        }
    }

    public void readExecutePrintLoop() throws IOException {
        System.out.println("Eureka 2.0 Command Line Client");
        while (true) {
            String line = consoleReader.readLine("> ");
            if (line != null && !(line = line.trim()).isEmpty()) {
                if (executeLine(line)) {
                    return;
                }
            }
        }
    }

    private boolean executeLine(String line) {
        for (String cmdLine : line.split("&&")) {
            if (!executeCommand(cmdLine)) {
                return true;
            }
        }
        return false;
    }

    private boolean executeCommand(String cmdLine) {
        if ((cmdLine = cmdLine.trim()).isEmpty()) {
            return true;
        }

        if (aliasMap.containsKey(cmdLine)) {
            executeLine(aliasMap.get(cmdLine));
        }

        String[] parts = cmdLine.split("\\s+");
        String cmd = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        if ("quit".equals(cmd)) {
            saveHistory();
            System.out.println("Terminatting...");
            return false;
        }
        try {
            if ("help".equals(cmd) && expect(cmd, 0, args)) {
                runHelp();
            } else if ("history".equals(cmd) && expect(cmd, 0, args)) {
                runHistory();
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
            } else if ("show".equals(cmd) && expect(cmd, 0, args)) {
                listenForRegistry();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return true;
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
        System.out.println("  history                                              print previous commands   ");
        System.out.println("  quit                                                 exit                      ");
        System.out.println("  register                                             register with server      ");
        System.out.println("  update <field> <value>                               update own registry entry ");
        System.out.println("  status                                               print status summary      ");
        System.out.println("  show                                                 starts showing registry information");

        if (!aliasMap.isEmpty()) {
            System.out.println();
            System.out.println("Aliases");
            for (String key : aliasMap.keySet()) {
                System.out.format("  %-52s %s\n", key, aliasMap.get(key));
            }
        }
    }

    private void runHistory() {
        for (int i = 0; i < consoleReader.getHistory().size(); i++) {
            System.out.println(String.format("%4d %s", i + 1, consoleReader.getHistory().get(i)));
        }
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
        System.out.format("Connected to Eureka server at %s:%d (registry) and %s:%d (discovery)\n", host, registrationPort, host, discoveryPort);
    }

    private void runRegister() {
        if (!isConnected() || !expectedRegistrationStatus(Status.NotStarted, Status.Failed)) {
            return;
        }

        registrationStatus = Status.Initiated;
        lastInstanceInfo = SampleInstanceInfo.ZuulServer.builder()
                .withId(Integer.toString(idGenerator.getAndIncrement()))
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

    private void listenForRegistry() {
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

        registryFetchStatus = Status.Initiated;
        eurekaClient.forInterest(Interests.forFullRegistry())
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
                                System.out.println("Instance Added: " + notification.getData());
                                break;
                            case Delete:
                                System.out.println("Instance deleted: " + notification.getData());
                                break;
                            case Modify:
                                System.out.println("Instance updated: " + notification.getData());
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
    }

    public static void main(String[] args) throws IOException {
        new EurekaCLI().readExecutePrintLoop();
    }
}
