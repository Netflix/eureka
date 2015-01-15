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

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.Arrays;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.netflix.eureka2.testkit.cli.command.CloseCommand;
import com.netflix.eureka2.testkit.cli.command.ConnectCommand;
import com.netflix.eureka2.testkit.cli.command.InterestCommand;
import com.netflix.eureka2.testkit.cli.command.RegisterCommand;
import com.netflix.eureka2.testkit.cli.command.StatusCommand;
import com.netflix.eureka2.testkit.cli.command.UnregisterCommand;
import com.netflix.eureka2.testkit.cli.command.UpdateCommand;
import com.netflix.eureka2.transport.EurekaTransports.Codec;
import jline.Terminal;
import jline.TerminalFactory;
import jline.console.ConsoleReader;
import jline.console.history.History;
import jline.console.history.History.Entry;

/**
 * Simple command line Eureka client interface.
 *
 * @author Tomasz Bak
 */
@SuppressWarnings("CallToPrintStackTrace")
public class EurekaCLI {

    private static final File CONFIGURATION_FILE =
            System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home"), ".eurekarc");

    private static final File HISTORY_FILE =
            System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home"), ".eureka_history");

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\s*include\\s+(.+)\\s*");
    private static final Pattern ALIAS_DEF_PATTERN = Pattern.compile("\\s*alias\\s+([\\w\\d-_]+)=(.*)");

    private final Command[] commands = {
            new HelpCommand(),
            new AliasCommand(),
            new HistoryCommand(),
            new ReloadCommand(),
            ConnectCommand.toRegister(),
            ConnectCommand.toSubscribe(),
            ConnectCommand.toCluster(),
            new RegisterCommand(),
            new UpdateCommand(),
            new UnregisterCommand(),
            new CloseCommand(),
            InterestCommand.forFullRegistry(),
            InterestCommand.forVips(),
            new StatusCommand()
    };

    enum CmdResult {Ok, Error, Quit}

    private final Context context;
    private final ConsoleReader consoleReader;
    private final TreeMap<String, String> aliasMap = new TreeMap<>();

    public EurekaCLI(Codec codec) throws IOException {
        this.context = new Context(codec);
        Terminal terminal = TerminalFactory.create();
        terminal.setEchoEnabled(false);
        consoleReader = new ConsoleReader(System.in, System.out, terminal);
        loadConfiguration();
        loadHistory();
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {
                context.close();
            }
        }));
    }

    private void loadConfiguration() {
        aliasMap.clear();

        if (CONFIGURATION_FILE != null && CONFIGURATION_FILE.exists()) {
            try {
                loadConfigurationFrom(CONFIGURATION_FILE);
            } catch (IOException ignored) {
                System.err.println("ERROR: could not load configuration file from ~/.eureka_history");
            }
        }
    }

    private void loadConfigurationFrom(File file) throws IOException {
        System.out.println("Loading configuration from " + file + "...");
        try (LineNumberReader reader = new LineNumberReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                // Remove comments and empty lines
                line = line.trim();
                int cpos = line.indexOf('#');
                if (cpos != -1) {
                    line = line.substring(0, cpos);
                }
                if (!line.isEmpty()) {
                    Matcher includeMatcher = INCLUDE_PATTERN.matcher(line);
                    if (includeMatcher.matches()) {
                        String fileName = includeMatcher.group(1);
                        File nestedFile;
                        if (fileName.startsWith("/")) {
                            nestedFile = new File(fileName);
                        } else {
                            nestedFile = new File(CONFIGURATION_FILE.getParentFile(), fileName);
                        }
                        loadConfigurationFrom(nestedFile);
                    } else {
                        Matcher aliasMatcher = ALIAS_DEF_PATTERN.matcher(line);
                        if (aliasMatcher.matches()) {
                            aliasMap.put(aliasMatcher.group(1), aliasMatcher.group(2));
                        } else {
                            System.err.format("WARN: syntax error at %s#%d: %s...\n", file, reader.getLineNumber(),
                                    line.substring(0, Math.min(32, line.length())));
                        }
                    }
                }
            }
        }
    }

    private void loadHistory() {
        if (HISTORY_FILE != null && HISTORY_FILE.exists()) {
            try {
                try (LineNumberReader reader = new LineNumberReader(new FileReader(HISTORY_FILE))) {
                    History history = consoleReader.getHistory();
                    String line;
                    while ((line = reader.readLine()) != null) {
                        history.add(line);
                    }
                }
            } catch (IOException ignored) {
                System.err.println("ERROR: could not load history file from ~/.eureka_history");
            }
        }
    }

    private void saveHistory() {
        if (HISTORY_FILE != null) {
            if (HISTORY_FILE.exists()) {
                boolean deleted = HISTORY_FILE.delete();
                if (!deleted) {
                    System.err.println("Failed to delete the history file.");
                }
            }
            try {
                ListIterator<Entry> iterator = consoleReader.getHistory().entries();
                try (FileWriter writer = new FileWriter(HISTORY_FILE)) {
                    while (iterator.hasNext()) {
                        writer.write(iterator.next().value().toString());
                        writer.write('\n');
                    }
                }
            } catch (IOException ignored) {
                System.err.println("ERROR: could not save history file into ~/.eureka_history");
            }
        }
    }

    public void readExecutePrintLoop() throws IOException {
        System.out.println("Eureka 2.0 Command Line Client");
        while (true) {
            String line = consoleReader.readLine("> ");
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
            saveHistory();
            System.out.println("Terminating...");
            return CmdResult.Quit;
        }
        try {
            boolean matched = false;
            for (Command c : commands) {
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

    public static void main(String[] args) throws IOException {
        Codec codec = null;
        switch (args.length) {
            case 0:
                codec = Codec.Avro;
                break;
            case 2:
                if ("-c".equals(args[0])) {
                    codec = Codec.valueOf(args[1]);
                    break;
                }
            default:
                System.err.println("ERROR: invalid command line parameters; expected none or '-c <codec_name>");
                System.exit(-1);
        }
        new EurekaCLI(codec).readExecutePrintLoop();
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
            for (Command cmd : commands) {
                maxLen = Math.max(cmd.getInvocationSyntax().length(), maxLen);
            }
            maxLen += 2;
            String lineFormat = "  %-" + maxLen + "s%s\n";
            for (Command cmd : commands) {
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
            return "reload " + CONFIGURATION_FILE + " file";
        }

        @Override
        protected boolean executeCommand(Context context, String[] args) {
            loadConfiguration();
            return true;
        }
    }
}
