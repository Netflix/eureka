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

package com.netflix.eureka2.testkit.cli;

import java.io.IOException;
import java.util.Arrays;
import java.util.TreeMap;

import com.netflix.eureka2.codec.CodecType;
import com.netflix.eureka2.config.BasicEurekaTransportConfig;
import com.netflix.eureka2.config.EurekaTransportConfig;
import com.netflix.eureka2.ext.grpc.model.GrpcModelsInjector;
import com.netflix.eureka2.protocol.StdProtocolModel;
import com.netflix.eureka2.spi.protocol.ProtocolModel;
import com.netflix.eureka2.testkit.cli.command.CloseCommand;
import com.netflix.eureka2.testkit.cli.command.StatusCommand;
import com.netflix.eureka2.testkit.cli.command.bootstrap.BootstrapCommand;
import com.netflix.eureka2.testkit.cli.command.connect.ConnectCanonicalCommand;
import com.netflix.eureka2.testkit.cli.command.connect.ConnectReadClusterCommand;
import com.netflix.eureka2.testkit.cli.command.connect.ConnectReadNodeCommand;
import com.netflix.eureka2.testkit.cli.command.connect.ConnectWriteClusterCommand;
import com.netflix.eureka2.testkit.cli.command.connect.ConnectWriteNodeCommand;
import com.netflix.eureka2.testkit.cli.command.session.DescribeCommand;
import com.netflix.eureka2.testkit.cli.command.session.DisconnectCommand;
import com.netflix.eureka2.testkit.cli.command.session.InterestCommand;
import com.netflix.eureka2.testkit.cli.command.session.QueryCommand;
import com.netflix.eureka2.testkit.cli.command.session.RegisterCommand;
import com.netflix.eureka2.testkit.cli.command.session.SearchCommand;
import com.netflix.eureka2.testkit.cli.command.session.UnregisterCommand;
import com.netflix.eureka2.testkit.cli.command.session.UpdateCommand;
import com.netflix.eureka2.transport.EurekaTransports;
import com.netflix.eureka2.transport.StdEurekaTransportFactory;
import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;

/**
 * Simple command line Eureka client interface.
 *
 * @author Tomasz Bak
 */
@SuppressWarnings("CallToPrintStackTrace")
public class EurekaCLI {

    static {
        GrpcModelsInjector.injectGrpcModels();
        EurekaTransports.setTransportFactory(new StdEurekaTransportFactory());
        ProtocolModel.setDefaultModel(StdProtocolModel.getStdModel());
    }

    private final Command[] sharedCommands = {
            new HelpCommand(),
            new AliasCommand(),
            new HistoryCommand(),
            new ReloadCommand(),
            new StatusCommand()
    };

    private final Command[] bootstrapCommands = merge(sharedCommands,
            new BootstrapCommand()
    );

    private final Command[] connectCommands = merge(sharedCommands,
            new ConnectWriteClusterCommand(),
            new ConnectWriteNodeCommand(),
            new ConnectReadClusterCommand(),
            new ConnectReadNodeCommand(),
            new ConnectCanonicalCommand()
    );

    private final Command[] sessionCommands = merge(sharedCommands,
            new RegisterCommand(),
            new UpdateCommand(),
            new UnregisterCommand(),
            new DisconnectCommand(),
            new CloseCommand(),
            InterestCommand.forFullRegistry(),
            InterestCommand.forApps(),
            InterestCommand.forVips(),
            InterestCommand.forSecureVips(),
            new QueryCommand(),
            new DescribeCommand(),
            new SearchCommand()
    );


    enum CmdResult {Ok, Error, Quit;}

    private final EurekaTransportConfig transportConfig;

    private final ConfigurationLoader configurationLoader = new ConfigurationLoader();

    private final ConsoleReader consoleReader;

    private TreeMap<String, String> aliasMap;
    private final Context context = new Context();

    public EurekaCLI(EurekaTransportConfig transportConfig) throws IOException {
        this.transportConfig = transportConfig;
        Terminal terminal = TerminalFactory.create();
        terminal.setEchoEnabled(false);
        consoleReader = new ConsoleReader(System.in, System.out, terminal);
        this.aliasMap = configurationLoader.loadConfiguration();
        configurationLoader.loadHistory(consoleReader);
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                context.close();
            }
        }));
    }

    public void readExecutePrintLoop() throws IOException {
        System.out.println("Eureka 2.0 Command Line Client");
        while (true) {
            String prompt;
            if (context.getActiveSession() != null) {
                prompt = context.getActiveSession().getPrompt() + "> ";
            } else if (context.hasClusterResolver()) {
                prompt = "configured> ";
            } else {
                prompt = "bootstrap> ";
            }
            String line = consoleReader.readLine(prompt);
            if (line != null && !(line = line.trim()).isEmpty()) {
                if (executeLine(line) == CmdResult.Quit) {
                    return;
                }
            }
        }
    }

    private CmdResult executeLine(String line) {
        for (String cmdLine : line.split("&&")) {
            CmdResult cmdResult = executeCommand(cmdLine);
            if (cmdResult != CmdResult.Ok) {
                return cmdResult;
            }
        }
        return CmdResult.Ok;
    }

    private CmdResult executeCommand(String cmdLine) {
        if ((cmdLine = cmdLine.trim()).isEmpty()) {
            return CmdResult.Ok;
        }

        if (aliasMap.containsKey(cmdLine)) {
            return executeLine(aliasMap.get(cmdLine));
        }

        String[] parts = cmdLine.split("\\s+");
        String cmd = parts[0];
        String[] args = Arrays.copyOfRange(parts, 1, parts.length);
        if ("quit".equals(cmd)) {
            configurationLoader.saveHistory(consoleReader);
            System.out.println("Terminating...");
            return CmdResult.Quit;
        }
        try {
            boolean matched = false;
            for (Command c : getActiveCommands()) {
                if (cmd.equals(c.getName())) {
                    c.execute(context, args);
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                System.err.println("ERROR: not recognized command " + cmd);
                return CmdResult.Error;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return CmdResult.Ok;
    }

    private Command[] getActiveCommands() {
        if (context.getActiveSession() != null) {
            return sessionCommands;
        }
        if (context.hasClusterResolver()) {
            return connectCommands;
        }
        return bootstrapCommands;
    }

    private static Command[] merge(Command[] sharedCommands, Command... mainCommands) {
        Command[] merged = new Command[sharedCommands.length + mainCommands.length];
        System.arraycopy(sharedCommands, 0, merged, 0, sharedCommands.length);
        System.arraycopy(mainCommands, 0, merged, sharedCommands.length, mainCommands.length);
        return merged;
    }

    public static void main(String[] args) throws IOException {
        CodecType codec = null;
        switch (args.length) {
            case 0:
                codec = CodecType.Avro;
                break;
            case 2:
                if ("-c".equals(args[0])) {
                    codec = CodecType.valueOf(args[1]);
                    break;
                }
            default:
                System.err.println("ERROR: invalid command line parameters; expected none or '-c <codec_name>");
                System.exit(-1);
        }
        EurekaTransportConfig transportConfig = new BasicEurekaTransportConfig.Builder().withCodec(codec).build();
        new EurekaCLI(transportConfig).readExecutePrintLoop();
    }

    class HelpCommand extends Command {

        HelpCommand() {
            super("help");
        }

        @Override
        public String getDescription() {
            return "print this help";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            System.out.println("Available commands:");
            int maxLen = -1;
            for (Command cmd : getActiveCommands()) {
                maxLen = Math.max(cmd.getInvocationSyntax().length(), maxLen);
            }
            maxLen += 2;
            String lineFormat = "  %-" + maxLen + "s%s\n";
            for (Command cmd : getActiveCommands()) {
                System.out.format(lineFormat, cmd.getInvocationSyntax(), cmd.getDescription());
            }
            return true;
        }
    }

    class AliasCommand extends Command {
        AliasCommand() {
            super("alias");
        }

        @Override
        public String getDescription() {
            return "print aliases";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            if (aliasMap.isEmpty()) {
                System.out.println("No aliases defined");
            } else {
                System.out.println("Defined aliases:");
                int maxLen = -1;
                for (String key : aliasMap.keySet()) {
                    maxLen = Math.max(key.length(), maxLen);
                }
                maxLen += 2;
                String lineFormat = "  %-" + maxLen + "s%s\n";
                for (String key : aliasMap.keySet()) {
                    System.out.format(lineFormat, key, aliasMap.get(key));
                }
            }
            return true;
        }
    }

    class HistoryCommand extends Command {

        HistoryCommand() {
            super("history", 0);
        }

        @Override
        public String getDescription() {
            return "print command history";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            for (int i = 0; i < consoleReader.getHistory().size(); i++) {
                System.out.println(String.format("%4d %s", i + 1, consoleReader.getHistory().get(i)));
            }
            return true;
        }
    }

    class ReloadCommand extends Command {

        protected ReloadCommand() {
            super("reload", 0);
        }

        @Override
        public String getDescription() {
            return "reload " + ConfigurationLoader.CONFIGURATION_FILE + " file";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            aliasMap = configurationLoader.loadConfiguration();
            return true;
        }
    }
}
