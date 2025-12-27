package io.jettra.memory;

import io.helidon.logging.common.LogConfig;
import java.util.logging.Logger;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        // Check for -sleep argument (used for hot-reloads)
        for (int i = 0; i < args.length; i++) {
            if ("-sleep".equals(args[i]) && i + 1 < args.length) {
                try {
                    long ms = Long.parseLong(args[i + 1]);
                    System.out.println("Sleeping for " + ms + "ms before startup...");
                    Thread.sleep(ms);
                } catch (Exception ignored) {
                }
            }
        }

        System.out.println("""
                       _      _   _
                      | |    | | | |
                      | | ___| |_| |_ _ __ __ _   _ __ ___   ___ _ __ ___   ___  _ __ _   _
                  _   | |/ _ \\ __| __| '__/ _` | | '_ ` _ \\ / _ \\ '_ ` _ \\ / _ \\| '__| | | |
                 | |__| |  __/ |_| |_| | | (_| | | | | | | |  __/ | | | | | (_) | |  | |_| |
                  \\____/ \\___|\\__|\\__|_|  \\__,_| |_| |_| |_|\\___|_| |_| |_|\\___/|_|   \\__, |
                                                                                       __/ |
                 Jettra Memory Server v1.0                                            |___/
                """);

        try {
            // Initialize logging
            LogConfig.configureRuntime();

            // Load config
            String configPath = "memory.json";
            // Find the first argument that is NOT a flag
            for (int i = 0; i < args.length; i++) {
                if (args[i].startsWith("-")) {
                    if (args[i].equals("-sleep"))
                        i++; // Skip the sleep value
                    continue;
                }
                configPath = args[i];
                break;
            }

            java.io.File configFile = new java.io.File(configPath);
            LOGGER.info("Using config file: " + configFile.getAbsolutePath());

            MemoryConfig config = MemoryConfig.loadFromFile(configPath);
            if (!configFile.exists()) {
                config.saveToFile(configPath);
                LOGGER.info("Created default memory.json");
            }

            // Start the server logic
            JettraMemoryServer.startServer(config);

        } catch (Exception e) {
            LOGGER.severe("Failed to start Jettra Memory server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
