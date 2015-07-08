package com.netflix.eureka2.testkit.cli;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.LineNumberReader;
import java.util.ListIterator;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jline.console.ConsoleReader;
import jline.console.history.History;
import jline.console.history.History.Entry;

/**
 * @author Tomasz Bak
 */
public class ConfigurationLoader {

    public static final File CONFIGURATION_FILE =
            System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home"), ".eurekarc");

    public static final File HISTORY_FILE =
            System.getProperty("user.home") == null ? null : new File(System.getProperty("user.home"), ".eureka_history");

    private static final Pattern INCLUDE_PATTERN = Pattern.compile("\\s*include\\s+(.+)\\s*");
    private static final Pattern ALIAS_DEF_PATTERN = Pattern.compile("\\s*alias\\s+([\\w\\d-_]+)=(.*)");

    public TreeMap<String, String> loadConfiguration() {
        if (CONFIGURATION_FILE != null && CONFIGURATION_FILE.exists()) {
            try {
                TreeMap<String, String> aliasMap = new TreeMap<>();
                return loadConfigurationFrom(CONFIGURATION_FILE, aliasMap);
            } catch (IOException ignored) {
                System.err.println("ERROR: could not load configuration file from ~/.eureka_history");
            }
        }
        return new TreeMap<>();
    }

    private TreeMap<String, String> loadConfigurationFrom(File file, TreeMap<String, String> aliasMap) throws IOException {
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
                        loadConfigurationFrom(nestedFile, aliasMap);
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
        return aliasMap;
    }

    public void loadHistory(ConsoleReader consoleReader) {
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

    public void saveHistory(ConsoleReader consoleReader) {
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
}
