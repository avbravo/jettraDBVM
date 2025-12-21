package io.jettra.federated;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
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
        List<String> federatedServers = new ArrayList<>();
        
        // 1. Try loading from cluster.json
        File configFile = new File("cluster.json");
        if (configFile.exists()) {
            try {
                Map<String, Object> config = mapper.readValue(configFile, Map.class);
                if (config.containsKey("FederatedServers")) {
                    federatedServers = (List<String>) config.get("FederatedServers");
                    LOGGER.info("Loaded FederatedServers from cluster.json: " + federatedServers);
                }
                // Optionally read port from config if specified for federated mode, but config usually has client port.
                // Assuming port 9000 as per previous code unless arg overrides.
            } catch (IOException e) {
                LOGGER.warning("Failed to read config.json: " + e.getMessage());
            }
        }

        // 2. Args override
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException e) {
                LOGGER.warning("Invalid port arg, using " + port);
            }
        }
        
        if (args.length > 2) {
             // If manual peers passed
             for (int i = 2; i < args.length; i++) {
                 federatedServers.add(args[i]);
             }
        }

        FederatedEngine engine = new FederatedEngine(port);
        engine.start();

        String nodeId = "fed-" + port; // Simple ID generation
        FederatedRaftNode raftNode = new FederatedRaftNode(nodeId, federatedServers, engine);
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
