package io.jettra.federated;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRouting;
import io.helidon.webserver.staticcontent.StaticContentService;

public class FederatedMain {
    private static final Logger LOGGER = Logger.getLogger(FederatedMain.class.getName());
    private static final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        int port = 9000; // Default federated port
        String nodeId = null; 
        boolean bootstrap = false;
        List<String> federatedServers = new java.util.concurrent.CopyOnWriteArrayList<>();
        
        // 1. Try loading from federated.json
        File configFile = new File("federated.json");
        if (configFile.exists()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> config = mapper.readValue(configFile, Map.class);
                if (config.containsKey("FederatedServers")) {
                    @SuppressWarnings("unchecked")
                    List<String> peers = (List<String>) config.get("FederatedServers");
                    federatedServers.addAll(peers);
                    LOGGER.info("Loaded FederatedServers from federated.json: " + federatedServers);
                }
                if (config.containsKey("Port")) {
                    port = (Integer) config.get("Port");
                }
                if (config.containsKey("NodeID")) {
                    nodeId = (String) config.get("NodeID");
                }
                if (config.containsKey("Bootstrap")) {
                    bootstrap = (Boolean) config.get("Bootstrap");
                }
            } catch (IOException e) {
                LOGGER.warning("Failed to read federated.json: " + e.getMessage());
            }
        }

        // 2. Args override
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals("-bootstrap")) {
                bootstrap = true;
            } else if (i == 0) {
                try {
                    port = Integer.parseInt(args[i]);
                } catch (NumberFormatException e) {
                    LOGGER.warning("Invalid port arg, using " + port);
                }
            } else if (i == 1) {
                nodeId = args[i];
            } else if (i >= 2) {
                 federatedServers.add(args[i]);
            }
        }

        if (nodeId == null) {
            nodeId = "fed-" + port;
        }

        final int finalPort = port;
        final String finalNodeId = nodeId;

        FederatedEngine engine = new FederatedEngine(port);
        engine.start();

        String selfUrl = "http://localhost:" + port;
        // Search in federatedServers for a matching URL to be more precise if possible
        for (String url : federatedServers) {
            if (url.contains(":" + port)) {
                selfUrl = url;
                break;
            }
        }

        // Config saver
        java.util.function.Consumer<List<String>> configSaver = (peers) -> {
            try {
                File cfgFile = new File("federated.json");
                Map<String, Object> config = new java.util.HashMap<>();
                if (cfgFile.exists()) {
                    config = mapper.readValue(cfgFile, Map.class);
                }
                config.put("FederatedServers", peers);
                // Also ensures other fields are present if file didn't exist or was empty?
                // Minimal config preservation
                if (!config.containsKey("Port")) config.put("Port", finalPort);
                if (!config.containsKey("NodeID")) config.put("NodeID", finalNodeId);
                if (!config.containsKey("Mode")) config.put("Mode", "federated");
                
                mapper.writerWithDefaultPrettyPrinter().writeValue(cfgFile, config);
                LOGGER.info("Updated federated.json with new peers: " + peers);
            } catch (IOException e) {
                LOGGER.severe("Failed to save federated.json: " + e.getMessage());
            }
        };

        FederatedRaftNode raftNode = new FederatedRaftNode(nodeId, selfUrl, federatedServers, engine, bootstrap, configSaver);
        raftNode.start();

        WebServer server = WebServer.builder()
                .port(port)
                .routing(HttpRouting.builder()
                        .register("/", StaticContentService.builder("web")
                                .welcomeFileName("index.html")
                                .build())
                        .register("/federated", new FederatedService(engine, raftNode))
                        .get("/health", (req, res) -> res.send("OK")))
                .build()
                .start();

        LOGGER.log(Level.INFO, "Jettra Federated Server started on port {0}", port);
        
        // Prevent immediate exit
        try {
            Thread.sleep(Long.MAX_VALUE);
        } catch (InterruptedException e) {
            server.stop();
        }
    }
}
