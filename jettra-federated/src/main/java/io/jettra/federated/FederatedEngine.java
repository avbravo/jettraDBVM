package io.jettra.federated;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FederatedEngine {
    private static final Logger LOGGER = Logger.getLogger(FederatedEngine.class.getName());
    private final int port;
    private final Map<String, Map<String, Object>> dbNodes = new ConcurrentHashMap<>();
    private String currentLeaderId = null;
    private volatile boolean isFederatedLeader = false;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final ObjectMapper mapper = new ObjectMapper();
    private final File stateFile = new File("federated_cluster.json");

    public FederatedEngine(int port) {
        this.port = port;
        loadState();
    }

    public Map<String, Map<String, Object>> getDbNodes() {
        return dbNodes;
    }

    public void onBecomeLeader() {
        this.isFederatedLeader = true;
        LOGGER.info("This node is now the FEDERATED LEADER. Taking control of DB cluster.");
        checkNodesHealth(); // Immediate check
    }

    public void onStepDown() {
        this.isFederatedLeader = false;
        LOGGER.info("This node stepped down from Federated Leadership.");
    }
    
    public void start() {
        LOGGER.info("Federated Engine starting...");
        scheduler.scheduleAtFixedRate(this::checkNodesHealth, 5, 5, TimeUnit.SECONDS);
    }

    @SuppressWarnings("unchecked")
    private void loadState() {
        if (stateFile.exists()) {
            try {
                Map<String, Object> state = mapper.readValue(stateFile, Map.class);
                this.currentLeaderId = (String) state.get("leaderId");
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) state.get("nodes");
                if (nodes != null) {
                    for (Map<String, Object> node : nodes) {
                        dbNodes.put((String) node.get("id"), node);
                    }
                }
                LOGGER.info("Loaded federated state from " + stateFile.getName());
            } catch (IOException e) {
                LOGGER.warning("Failed to load federated state: " + e.getMessage());
            }
        }
    }

    private synchronized void saveState() {
        try {
            Map<String, Object> state = new HashMap<>();
            state.put("leaderId", currentLeaderId);
            state.put("nodes", new ArrayList<>(dbNodes.values()));
            mapper.writerWithDefaultPrettyPrinter().writeValue(stateFile, state);
        } catch (IOException e) {
            LOGGER.severe("Failed to save federated state: " + e.getMessage());
        }
    }

    public synchronized void registerNode(String nodeId, String url) {
        Map<String, Object> nodeInfo = dbNodes.getOrDefault(nodeId, new HashMap<>());
        nodeInfo.put("id", nodeId);
        nodeInfo.put("url", url);
        nodeInfo.put("status", "ACTIVE");
        nodeInfo.put("lastSeen", System.currentTimeMillis());
        dbNodes.put(nodeId, nodeInfo);
        
        if (currentLeaderId == null) {
            assignLeader(nodeId);
        } else {
            saveState();
        }
        LOGGER.info("Registered DB node: " + nodeId + " at " + url);
    }

    private synchronized void assignLeader(String nodeId) {
        this.currentLeaderId = nodeId;
        LOGGER.info("Assigned NEW LEADER: " + nodeId);
        saveState();
        
        // Call promote endpoint on the new leader
        Map<String, Object> node = dbNodes.get(nodeId);
        if (node != null && isFederatedLeader) {
            String url = (String) node.get("url");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/cluster/promote"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(res -> {
                        if (res.statusCode() == 200) {
                            LOGGER.info("Successfully promoted " + nodeId + " via API");
                            notifyAllNodesOfLeader();
                        } else {
                            LOGGER.warning("Failed to promote " + nodeId + " (Status: " + res.statusCode() + ")");
                        }
                    });
        }
    }

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofSeconds(2))
            .build();

    public int getPort() {
        return port;
    }

    private void notifyAllNodesOfLeader() {
        if (!isFederatedLeader || currentLeaderId == null) return;
        
        Map<String, Object> leaderNode = dbNodes.get(currentLeaderId);
        if (leaderNode == null) return;
        String leaderUrl = (String) leaderNode.get("url");

        // Tell the leader about all other nodes so it can update its _clusternodes
        // We iterate and send register/update commands to the Leader
        for (Map<String, Object> node : dbNodes.values()) {
             // We can optimize by sending a batch, but for now loop is fine
             try {
                String status = (String) node.getOrDefault("status", "ACTIVE");
                // Construct payload
                Map<String, Object> payload = new HashMap<>(node);
                // Ensure ID is set
                if (!payload.containsKey("_id")) payload.put("_id", node.get("id"));
                
                String json = mapper.writeValueAsString(payload);
                
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(leaderUrl + "/api/cluster/register"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json))
                        .build();
                        
                httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                        .thenAccept(res -> {
                            if (res.statusCode() != 200) {
                                LOGGER.warning("Failed to sync node " + node.get("id") + " to leader " + currentLeaderId);
                            }
                        });

             } catch (Exception e) {
                 LOGGER.warning("Error notifying leader: " + e.getMessage());
             }
        }
    }

    private void checkNodesHealth() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Map.Entry<String, Map<String, Object>> entry : dbNodes.entrySet()) {
            Map<String, Object> info = entry.getValue();
            if (!info.containsKey("lastSeen")) continue;
            long lastSeen = (long) info.get("lastSeen");
            if (now - lastSeen > 10000) { // Reduced to 10 seconds for better responsiveness
                if (!"INACTIVE".equals(info.get("status"))) {
                    info.put("status", "INACTIVE");
                    changed = true;
                    if (isFederatedLeader && entry.getKey().equals(currentLeaderId)) {
                        electNewLeader();
                    }
                }
            }
        }
        if (changed) saveState();
    }

    private synchronized void electNewLeader() {
        currentLeaderId = null;
        for (Map.Entry<String, Map<String, Object>> entry : dbNodes.entrySet()) {
            if ("ACTIVE".equals(entry.getValue().get("status"))) {
                assignLeader(entry.getKey());
                return;
            }
        }
        saveState();
    }

    public Map<String, Object> getClusterStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("leaderId", currentLeaderId);
        status.put("isFederatedLeader", isFederatedLeader);
        status.put("nodes", new ArrayList<>(dbNodes.values()));
        return status;
    }

    public void heartbeat(String nodeId) {
        Map<String, Object> info = dbNodes.get(nodeId);
        if (info != null) {
            info.put("lastSeen", System.currentTimeMillis());
            if ("INACTIVE".equals(info.get("status"))) {
                 info.put("status", "ACTIVE");
                 saveState();
            }
        }
    }
}
