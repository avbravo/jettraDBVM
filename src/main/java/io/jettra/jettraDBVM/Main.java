package io.jettra.jettraDBVM;

import io.helidon.config.Config;
import io.helidon.logging.common.LogConfig;
import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.staticcontent.StaticContentService;
import io.jettra.jettraDBVM.web.WebServices;

import java.util.logging.Logger;
import java.util.Map;
import com.fasterxml.jackson.core.type.TypeReference;

public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static io.jettra.core.Engine engine;

    public static void main(String[] args) {
        try {
            // Initialize logging
            LogConfig.configureRuntime();

            // Load custom config.json
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.io.File configFile = new java.io.File("config.json");
            Map<String, Object> jettraConfig;

            if (configFile.exists()) {
                jettraConfig = mapper.readValue(configFile, new TypeReference<Map<String, Object>>() {
                });
            } else {
                // Create default config and write to file
                jettraConfig = new java.util.LinkedHashMap<>(); // Use LinkedHashMap to keep order if possible
                jettraConfig.put("Host", "0.0.0.0");
                jettraConfig.put("Port", 8080);
                jettraConfig.put("DataDir", "data");
                jettraConfig.put("Role", "Obsolete");
                jettraConfig.put("SessionTimeout", 0);
                jettraConfig.put("Bootstrap", true);
                jettraConfig.put("ClusterID", "cluster-1");
                jettraConfig.put("NodeID", "node-" + java.util.UUID.randomUUID().toString());
                
                mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, jettraConfig);
                LOGGER.info("Created default config.json");
            }

            // Allow override from Helidon Config for other things if needed, but primarily
            // use jettraConfig
            Config helidonConfig = Config.create();

            // Initialize Engine with the Map config
            engine = new io.jettra.core.Engine(jettraConfig);
            engine.start();

            int port = (int) jettraConfig.getOrDefault("Port", 8080);
            String host = (String) jettraConfig.getOrDefault("Host", "0.0.0.0");

            // Build WebServer
            WebServer server = WebServer.builder()
                    .port(port)
                    .host(host)
                    .routing(Main::routing)
                    .build()
                    .start();

            LOGGER.info("Server started at http://" + host + ":" + server.port());
        } catch (Exception e) {
            LOGGER.severe("Failed to start server: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void routing(HttpRules rules) {
        // API Services
        WebServices webServices = new WebServices(engine);
        webServices.register(rules);

        // Static Content (Dashboard)
        // Serve from classpath /WEB
        rules.register("/", StaticContentService.builder("WEB")
                .welcomeFileName("index.html")
                .build());
    }
}
