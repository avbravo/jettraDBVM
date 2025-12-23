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
        
        // Reconcile: If we think there is a leader, make sure it's promoted.
        // If not, elect a new one immediately.
        if (currentLeaderId != null) {
            Map<String, Object> node = dbNodes.get(currentLeaderId);
            if (node != null && "ACTIVE".equals(node.get("status"))) {
                LOGGER.info("Re-promoting current leader on federated leadership takeover: " + currentLeaderId);
                assignLeader(currentLeaderId);
            } else {
                electNewLeader();
            }
        } else {
            electNewLeader();
        }
        checkNodesHealth();
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
                        // Mark as UNKNOWN on startup so we don't assume they are active
                        node.put("status", "UNKNOWN");
                        dbNodes.put((String) node.get("id"), node);
                    }
                }
                LOGGER.info("Loaded federated state from " + stateFile.getName() + " (Nodes marked UNKNOWN until heartbeat)");
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
        
        // If we are federated leader and there is no DB leader, assign one.
        if (isFederatedLeader && (currentLeaderId == null || "INACTIVE".equals(dbNodes.get(currentLeaderId).get("status")))) {
            assignLeader(nodeId);
        } else {
            saveState();
        }
        LOGGER.info(() -> String.format("Registered DB node: %s at %s", nodeId, url));
    }

    private synchronized void assignLeader(String nodeId) {
        this.currentLeaderId = nodeId;
        LOGGER.info(() -> String.format("Assigned NEW LEADER: %s", nodeId));
        saveState();
        
        // Call promote endpoint on the new leader
        Map<String, Object> node = dbNodes.get(nodeId);
        if (node != null && isFederatedLeader) {
            String url = (String) node.get("url");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/cluster/promote"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(java.time.Duration.ofSeconds(3))
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding())
                    .thenAccept(res -> {
                        if (res.statusCode() == 200) {
                            LOGGER.info(() -> String.format("Successfully promoted %s via API", nodeId));
                            notifyAllNodesOfLeader();
                        } else {
                            LOGGER.warning(() -> String.format("Failed to promote %s (Status: %d)", nodeId, res.statusCode()));
                        }
                    })
                    .exceptionally(ex -> {
                        LOGGER.warning(() -> "Error promoting node " + nodeId + ": " + ex.getMessage());
                        return null;
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

        for (Map<String, Object> node : dbNodes.values()) {
             try {
                Map<String, Object> payload = new HashMap<>(node);
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
                                LOGGER.warning(() -> String.format("Failed to sync node %s to leader %s", node.get("id"), currentLeaderId));
                            }
                        });

             } catch (IOException e) {
                 LOGGER.warning(() -> "Error notifying leader: " + e.getMessage());
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
            String status = (String) info.getOrDefault("status", "ACTIVE");
            
            if (now - lastSeen > 10000) { 
                if (!"INACTIVE".equals(status)) {
                    info.put("status", "INACTIVE");
                    changed = true;
                    if (isFederatedLeader && entry.getKey().equals(currentLeaderId)) {
                        electNewLeader();
                    }
                }
            } else if ("ACTIVE".equals(status)) {
                // Fetch metrics if active
                fetchNodeMetrics(entry.getKey(), (String) info.get("url"));
            }
        }
        if (changed) saveState();
    }

    private void fetchNodeMetrics(String nodeId, String url) {
        if (url == null) return;
        long startTime = System.currentTimeMillis();
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/metrics"))
                    .GET()
                    .timeout(java.time.Duration.ofSeconds(2))
                    .build();
            
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        long latency = System.currentTimeMillis() - startTime;
                        if (res.statusCode() == 200) {
                            try {
                                Map<String, Object> metrics = mapper.readValue(res.body(), Map.class);
                                metrics.put("latency", latency);
                                Map<String, Object> info = dbNodes.get(nodeId);
                                if (info != null) {
                                    info.put("metrics", metrics);
                                }
                            } catch (Exception e) {}
                        }
                    });
        } catch (Exception e) {}
    }

    public void stopNode(String nodeId) {
        Map<String, Object> node = dbNodes.get(nodeId);
        if (node != null) {
            String url = (String) node.get("url");
            // Call stop endpoint. Note: /raft/rpc/stop or /api/cluster/restart?
            // User requested "detener" which usually means permanent or at least exit(0).
            // We'll use the raft stop endpoint which is System.exit(0)
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/raft/rpc/stop"))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.discarding());
        }
    }

    public void removeNode(String nodeId) {
        if (dbNodes.remove(nodeId) != null) {
            if (nodeId.equals(currentLeaderId)) {
                electNewLeader();
            } else {
                saveState();
            }
            LOGGER.info(() -> String.format("Removed DB node: %s", nodeId));
        }
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

    @SuppressWarnings("unchecked")
    public synchronized void applyClusterState(Map<String, Object> state) {
        if (state == null) return;
        
        String newDbLeader = (String) state.get("leaderId");
        if (newDbLeader != null) {
            this.currentLeaderId = newDbLeader;
        }

        List<Map<String, Object>> nodes = (List<Map<String, Object>>) state.get("nodes");
        if (nodes != null) {
            for (Object nodeObj : nodes) {
                if (nodeObj instanceof Map) {
                    Map<String, Object> node = (Map<String, Object>) nodeObj;
                    String id = (String) node.get("id");
                    if (id != null) {
                        dbNodes.put(id, new HashMap<>(node));
                    }
                }
            }
        }
        saveState();
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
