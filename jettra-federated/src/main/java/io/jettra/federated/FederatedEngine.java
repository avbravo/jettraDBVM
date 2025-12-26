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
        LOGGER.info("This node is now the FEDERATED LEADER (" + port + "). Taking control of DB cluster.");
        
        // Reconcile: If we think there is a leader, make sure it's promoted.
        // If not, elect a new one immediately.
        if (currentLeaderId != null) {
            Map<String, Object> node = dbNodes.get(currentLeaderId);
            if (node != null && "ACTIVE".equals(node.get("status"))) {
                LOGGER.info("Re-promoting current leader on federated leadership takeover: " + currentLeaderId);
                assignLeader(currentLeaderId);
            } else {
                LOGGER.info("Current leader " + currentLeaderId + " is not active/valid. Electing new one.");
                electNewLeader();
            }
        } else {
            LOGGER.info("No current leader ID. Electing new one.");
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
        scheduler.scheduleAtFixedRate(this::reconcileDbLeader, 10, 10, TimeUnit.SECONDS);
    }

    private void reconcileDbLeader() {
        if (!isFederatedLeader) return;
        
        if (currentLeaderId == null || !isNodeActive(currentLeaderId)) {
            LOGGER.info("Periodic health check: No active DB leader. Triggering election.");
            electNewLeader();
        }
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

    public synchronized void registerNode(String nodeId, String url, Map<String, Object> additionalInfo) {
        Map<String, Object> nodeInfo = dbNodes.get(nodeId);
        boolean wasNotActive = nodeInfo == null || !"ACTIVE".equals(nodeInfo.get("status"));
        
        if (nodeInfo == null) {
            nodeInfo = new HashMap<>();
            nodeInfo.put("id", nodeId);
            nodeInfo.put("url", url);
        }
        nodeInfo.put("status", "ACTIVE");
        nodeInfo.put("lastSeen", System.currentTimeMillis());
        if (additionalInfo != null) {
            nodeInfo.putAll(additionalInfo);
        }
        dbNodes.put(nodeId, nodeInfo);
        
        if (isFederatedLeader) {
            // Force re-evaluation of leader if none exists or current is inactive
            if (currentLeaderId == null || !isNodeActive(currentLeaderId)) {
                LOGGER.info("No active DB leader found during registration of " + nodeId + ". Electing...");
                electNewLeader();
            }
        }
        saveState();
        LOGGER.info(() -> String.format("Registered DB node: %s at %s", nodeId, url));
    }

    private boolean isNodeActive(String nodeId) {
        if (nodeId == null) return false;
        Map<String, Object> node = dbNodes.get(nodeId);
        return node != null && "ACTIVE".equals(node.get("status"));
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
            String status = (String) info.getOrDefault("status", "ACTIVE");
            
            if (!info.containsKey("lastSeen")) {
                if (isFederatedLeader && entry.getKey().equals(currentLeaderId)) {
                    LOGGER.info("Leader " + currentLeaderId + " has no heartbeat. Clearing.");
                    electNewLeader();
                    changed = true;
                }
                continue;
            }

            long lastSeen = (long) info.get("lastSeen");
            
            if (now - lastSeen > 10000) { 
                if (!"INACTIVE".equals(status)) {
                    info.put("status", "INACTIVE");
                    changed = true;
                    if (isFederatedLeader && entry.getKey().equals(currentLeaderId)) {
                        LOGGER.info("Leader " + currentLeaderId + " timed out. Electing new one.");
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

    public void restartNode(String nodeId) {
        Map<String, Object> node = dbNodes.get(nodeId);
        if (node != null) {
            String url = (String) node.get("url");
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url + "/api/cluster/restart"))
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
        LOGGER.info("Starting election for DB Cluster Leader...");
        currentLeaderId = null;
        for (Map.Entry<String, Map<String, Object>> entry : dbNodes.entrySet()) {
            if ("ACTIVE".equals(entry.getValue().get("status"))) {
                LOGGER.info("Found active node: " + entry.getKey() + ". Assigning as leader.");
                assignLeader(entry.getKey());
                return;
            }
        }
        LOGGER.warning("No active nodes found in DB Cluster. Leader remains unassigned.");
        saveState();
    }

    private final Map<String, Map<String, Object>> memoryNodes = new ConcurrentHashMap<>();
    private String currentMemoryLeaderId = null;

    public Map<String, Map<String, Object>> getMemoryNodes() {
        return memoryNodes;
    }

    public synchronized void registerMemoryNode(String nodeId, String url, Map<String, Object> additionalInfo) {
        Map<String, Object> nodeInfo = memoryNodes.getOrDefault(nodeId, new HashMap<>());
        nodeInfo.put("id", nodeId);
        nodeInfo.put("url", url);
        nodeInfo.put("status", "ACTIVE");
        nodeInfo.put("lastSeen", System.currentTimeMillis());
        if (additionalInfo != null) {
            nodeInfo.putAll(additionalInfo);
        }
        memoryNodes.put(nodeId, nodeInfo);
        LOGGER.info(() -> String.format("Registered Memory node: %s at %s", nodeId, url));
        
        // Elect memory leader if none or inactive
        if (currentMemoryLeaderId == null || !isActiveMemory(currentMemoryLeaderId)) {
            assignMemoryLeader();
        }
    }
    
    private boolean isActiveMemory(String id) {
        Map<String, Object> node = memoryNodes.get(id);
        return node != null && "ACTIVE".equals(node.get("status"));
    }

    private synchronized void assignMemoryLeader() {
        String previousLeaderId = currentMemoryLeaderId;
        currentMemoryLeaderId = null;
        
        // Priority 1: Match host (prefer local memory node)
        String myHost = "localhost"; // Usually
        for (Map.Entry<String, Map<String, Object>> entry : memoryNodes.entrySet()) {
            if ("ACTIVE".equals(entry.getValue().get("status"))) {
                String url = (String) entry.getValue().get("url");
                if (url.contains("localhost") || url.contains("127.0.0.1") || (url.contains(myHost) && !myHost.isEmpty())) {
                    currentMemoryLeaderId = entry.getKey();
                    break;
                }
            }
        }
        
        // Priority 2: Any active node if no local one found
        if (currentMemoryLeaderId == null) {
            for (Map.Entry<String, Map<String, Object>> entry : memoryNodes.entrySet()) {
                if ("ACTIVE".equals(entry.getValue().get("status"))) {
                    currentMemoryLeaderId = entry.getKey();
                    break;
                }
            }
        }
        
        if (currentMemoryLeaderId != null) {
            LOGGER.info("Assigned MEMORY LEADER: " + currentMemoryLeaderId);
            if (!currentMemoryLeaderId.equals(previousLeaderId)) {
                syncMemoryLeaderWithDb();
            }
        }
    }
    
    public void syncMemoryLeaderWithDb() {
        if (!isFederatedLeader) return;
        
        String memoryUrl = getMemoryLeaderUrl();
        String dbUrl = (currentLeaderId != null) ? (String) dbNodes.get(currentLeaderId).get("url") : null;
        
        if (memoryUrl != null && dbUrl != null) {
            LOGGER.info("Requesting Memory Leader to sync with DB Leader: " + dbUrl);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(memoryUrl + "/api/sync?dbLeaderUrl=" + dbUrl))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .timeout(java.time.Duration.ofSeconds(60)) // Sync can take time
                    .build();
            
            httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        if (res.statusCode() == 200) {
                            LOGGER.info("Memory sync command accepted by " + currentMemoryLeaderId);
                        } else {
                            LOGGER.warning("Memory sync command failed: " + res.body());
                        }
                    });
        }
    }
    
    /**
     * Determines the closest/best memory node.
     * Logic: if current leader is active, use it. Else re-elect.
     */
    public String getMemoryLeaderUrl() {
        if (currentMemoryLeaderId != null && isActiveMemory(currentMemoryLeaderId)) {
            return (String) memoryNodes.get(currentMemoryLeaderId).get("url");
        }
        if (isFederatedLeader) {
            assignMemoryLeader();
            if (currentMemoryLeaderId != null) {
                return (String) memoryNodes.get(currentMemoryLeaderId).get("url");
            }
        }
        return null;
    }
    
    /**
     * Unified CRUD Operation:
     * 1. Execute on Memory Leader (Sync)
     * 2. Execute on DB Cluster Leader (Async)
     */
    public String executeCrudpOperation(String apiPath, String query, String method, byte[] payload) throws Exception {
        String memoryUrl = getMemoryLeaderUrl();
        String result = null;
        
        String fullPath = apiPath + (query != null && !query.isEmpty() ? "?" + query : "");

        if (memoryUrl != null) {
             // 1. Sync write to Memory
             LOGGER.info("Forwarding (Sync) to Memory Leader: " + memoryUrl + fullPath);
             result = sendRequestSync(memoryUrl + fullPath, method, payload);
        } else {
             LOGGER.warning("No Memory Leader available. Operations might be slow as we skip memory layer.");
        }
        
        // 2. Async write to DB Leader
        if (currentLeaderId != null) {
            Map<String, Object> node = dbNodes.get(currentLeaderId);
            if (node != null) {
                String dbUrl = (String) node.get("url");
                Executors.newSingleThreadExecutor().submit(() -> {
                     try {
                         LOGGER.info("Forwarding (Async) to Persistent Leader: " + dbUrl + fullPath);
                         sendRequestSync(dbUrl + fullPath, method, payload);
                     } catch(Exception e) {
                         LOGGER.severe("Async persistence failed for " + fullPath + ": " + e.getMessage());
                     }
                });
            }
        }
        
        // If memory was available, return its result.
        // If not, we might want to wait for DB leader sync if it was a GET? 
        // But Crudp usually implies mutations.
        if (result == null && currentLeaderId != null) {
             // If memory skipped, we could wait for DB sync here, but for now we follow the "fast" model
             // where we return something quickly or a placeholder.
             return "{\"status\":\"accepted_async\"}";
        }
        
        return result != null ? result : "{\"status\":\"error\",\"message\":\"no_active_nodes\"}";
    }

    private String sendRequestSync(String url, String method, byte[] payload) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json");
        
        if ("POST".equalsIgnoreCase(method)) {
            builder.POST(HttpRequest.BodyPublishers.ofByteArray(payload));
        } else if ("PUT".equalsIgnoreCase(method)) {
            builder.PUT(HttpRequest.BodyPublishers.ofByteArray(payload));
        } else if ("DELETE".equalsIgnoreCase(method)) {
            builder.DELETE();
        } else {
            builder.GET();
        }

        HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new Exception("HTTP Error " + response.statusCode() + ": " + response.body());
        }
        return response.body();
    }

    public Map<String, Object> getClusterStatus() {
        Map<String, Object> status = new HashMap<>();
        status.put("leaderId", currentLeaderId);
        status.put("isFederatedLeader", isFederatedLeader);
        status.put("nodes", new ArrayList<>(dbNodes.values()));
        status.put("memoryNodes", new ArrayList<>(memoryNodes.values()));
        status.put("memoryLeaderId", currentMemoryLeaderId);
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

    public void heartbeat(String nodeId, Map<String, Object> additionalInfo) {
        Map<String, Object> info = dbNodes.get(nodeId);
        if (info != null) {
            boolean wasNotActive = !"ACTIVE".equals(info.get("status"));
            info.put("lastSeen", System.currentTimeMillis());
            if (additionalInfo != null) {
                info.putAll(additionalInfo);
            }
            if (wasNotActive) {
                 info.put("status", "ACTIVE");
                 saveState();
            }
            
            if (isFederatedLeader && wasNotActive) {
                if (currentLeaderId == null || !isNodeActive(currentLeaderId) || nodeId.equals(currentLeaderId)) {
                    assignLeader(nodeId);
                }
            }
        }
    }

    public void heartbeatMemory(String nodeId, Map<String, Object> additionalInfo) {
        Map<String, Object> info = memoryNodes.get(nodeId);
        if (info != null) {
            info.put("lastSeen", System.currentTimeMillis());
            if (additionalInfo != null) {
                info.putAll(additionalInfo);
            }
            if ("INACTIVE".equals(info.get("status"))) {
                 info.put("status", "ACTIVE");
                 saveState();
            }
        }
    }
}
