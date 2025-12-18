package io.jettra.core.raft;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Simplified Raft implementation for JettraDBVM.
 * Handles Leader Election, Log storage, and Dynamic Clustering via _clusternodes collection.
 */
public class RaftNode {
    private static final Logger LOGGER = Logger.getLogger(RaftNode.class.getName());

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    // Persistent state
    private int currentTerm;
    private String votedFor;
    private final List<LogEntry> log = new ArrayList<>();

    // Volatile state
    private int commitIndex = 0;
    private int lastApplied = 0;

    // Volatile state on Leader
    private Map<String, Integer> nextIndex;
    private Map<String, Integer> matchIndex;

    private State state = State.FOLLOWER;
    private final String nodeId;
    private final List<String> peers = new ArrayList<>();
    private String leaderId;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long lastHeartbeatTime;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newHttpClient();

    // Simplified timeouts (in ms)
    private static final int ELECTION_TIMEOUT_MIN = 3000;
    private static final int ELECTION_TIMEOUT_MAX = 6000;
    private static final int HEARTBEAT_INTERVAL = 500;

    private final io.jettra.core.config.ConfigManager configManager;
    private final io.jettra.core.storage.DocumentStore store;

    public RaftNode(String nodeId, List<String> peers, io.jettra.core.config.ConfigManager configManager,
            io.jettra.core.storage.DocumentStore store) {
        this.nodeId = nodeId;
        this.configManager = configManager;
        this.store = store;

        // Ensure collection exists
        try {
            store.createCollection("_system", "_clusternodes");
        } catch (Exception e) {
            // Likely already exists
        }

        loadPeersFromStore();

        boolean isBootstrap = configManager.isBootstrap();

        // If no peers found in DB and we are bootstrap, initialize self
        try {
            if (this.peers.isEmpty() && isBootstrap) {
                // Check simple query to be sure
                int count = store.count("_system", "_clusternodes");
                if (count == 0) {
                    Map<String, Object> self = new HashMap<>();
                    self.put("_id", nodeId);
                    self.put("url", "http://localhost:" + configManager.get("Port"));
                    self.put("role", "LEADER");
                    self.put("status", "ACTIVE");
                    store.save("_system", "_clusternodes", self);
                    
                    loadPeersFromStore();
                    
                    LOGGER.info("Bootstrap node initialized in _clusternodes. Becoming LEADER.");
                    becomeLeader();
                }
            } else if (this.peers.isEmpty() && peers != null && !peers.isEmpty()) {
                 // Fallback: if provided peers args but DB empty, maybe we should seed?
                 // For now, trust the DB or the register flow. 
                 // If we are a joiner, we wait for registration.
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error initializing cluster storage", e);
        }

        // Logic to enforce Bootstrap behavior on restart
        if (isBootstrap) {
            if (this.peers.isEmpty()) {
                LOGGER.info("Bootstrap node with no peers (or single node). Forcing LEADER role.");
                becomeLeader();
            } else {
                LOGGER.info("Bootstrap node with peers. Starting election to assert leadership.");
                // Schedule immediate election to assert leadership over the cluster
                scheduler.schedule(this::startElection, 50, TimeUnit.MILLISECONDS);
            }
        }

        this.currentTerm = 0;
        if (this.state != State.LEADER) {
            this.state = State.FOLLOWER;
        }

        this.lastHeartbeatTime = System.currentTimeMillis();

        // Start election timer loop
        scheduler.scheduleAtFixedRate(this::checkElectionTimeout, 1000, 200, TimeUnit.MILLISECONDS);
    }

    private void loadPeersFromStore() {
        this.peers.clear();
        try {
            List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
            Set<String> uniquePeers = new HashSet<>();
            for (Map<String, Object> node : nodes) {
                String id = (String) (node.get("_id") != null ? node.get("_id") : node.get("id"));
                String url = (String) node.get("url");
                String status = (String) node.get("status");
                
                // Only add ACTIVE nodes to peers list for replication efficiency
                if (id != null && !id.equals(this.nodeId) && url != null && "ACTIVE".equals(status)) {
                    uniquePeers.add(url);
                } else {
                    LOGGER.info("Skipping node from peers list: " + url + " (ID: " + id + ", Status: " + status + ")");
                }
            }
            this.peers.addAll(uniquePeers);
            LOGGER.info("Loaded peers from storage: " + peers);
        } catch (Exception e) {
            LOGGER.severe("Failed to load peers from storage: " + e.getMessage());
        }
    }

    public synchronized void start() {
        LOGGER.info("RaftNode started: " + nodeId + " Peers: " + peers);
    }

    public synchronized String getNodeId() {
        return nodeId;
    }

    public synchronized List<String> getPeers() {
        return peers;
    }

    public synchronized State getState() {
        return state;
    }

    public synchronized String getLeaderId() {
        return leaderId;
    }

    public synchronized int getCurrentTerm() {
        return currentTerm;
    }

    public synchronized boolean isLeader() {
        return state == State.LEADER;
    }

    private void checkElectionTimeout() {
        if (state == State.LEADER)
            return;
        
        // Fix: Do not start election if we are not initialized in the cluster storage
        // i.e. we are a fresh node waiting to be joined.
        try {
            int count = store.count("_system", "_clusternodes");
            if (count == 0) {
                 // We are empty, not bootstrapped. Wait for register/installSnapshot.
                 return;
            }
        } catch(Exception e) {
             // Store error?
        }

        long now = System.currentTimeMillis();
        // Randomized timeout
        long timeout = ELECTION_TIMEOUT_MIN + (long)(Math.random() * (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN));
        if (now - lastHeartbeatTime > timeout) {
            startElection();
        }
    }

    private void startElection() {
        LOGGER.info("Starting election...");
        int termForElection;
        synchronized (this) {
            state = State.CANDIDATE;
            currentTerm++;
            votedFor = nodeId;
            lastHeartbeatTime = System.currentTimeMillis();
            termForElection = currentTerm;
        }

        final AtomicInteger votes = new AtomicInteger(1); // Vote for self

        if (peers.isEmpty()) {
            // Single node cluster case:
            // Only become leader if we are actually the only one in the DB.
            // If DB is empty, checkElectionTimeout should have caught it, 
            // but double check to be safe.
             try {
                if (store.count("_system", "_clusternodes") > 0) {
                     becomeLeader();
                } else {
                    // Should not happen if checkElectionTimeout works, but revert to Follower
                    state = State.FOLLOWER;
                }
            } catch(Exception e) {
                state = State.FOLLOWER;
            }
            return;
        }

        String body = String.format("{\"term\":%d, \"candidateId\":\"%s\", \"lastLogIndex\":%d, \"lastLogTerm\":%d}",
                termForElection, nodeId, log.size(), 0);

        List<String> currentPeers = new ArrayList<>(peers); // Snapshot
        for (String peer : currentPeers) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(peer + "/raft/rpc/requestvote"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            try {
                                String json = response.body();
                                if (json.contains("\"voteGranted\":true") || json.contains("\"voteGranted\": true")) {
                                    int v = votes.incrementAndGet();
                                    if (v > (currentPeers.size() + 1) / 2) {
                                        becomeLeader();
                                    }
                                }
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error parsing vote response", e);
                            }
                        }
                    });
        }
    }

    private void becomeLeader() {
        if (state == State.LEADER) return;
        LOGGER.info("Becoming LEADER for term " + currentTerm);
        state = State.LEADER;
        leaderId = nodeId;
        configManager.setBootstrap(true);

        // Update self status in DB to ACTIVE / LEADER
        try {
            Map<String, Object> self = store.findByID("_system", "_clusternodes", nodeId);
            if (self != null) {
                self.put("role", "LEADER");
                self.put("status", "ACTIVE");
                store.update("_system", "_clusternodes", nodeId, self);
                
                // Replicate this update
                 Map<String, Object> cmd = new HashMap<>();
                cmd.put("op", "cluster_update_node");
                cmd.put("id", nodeId);
                cmd.put("data", self);
                replicate(cmd);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to update leader status in DB: " + e.getMessage());
        }

        // Start sending heartbeats
        scheduler.scheduleAtFixedRate(this::sendHeartbeats, 0, HEARTBEAT_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void sendHeartbeats() {
        if (state != State.LEADER)
            return;

        int termToSend;
        int commitToSend;
        List<String> peersSnapshot;
        synchronized (this) {
            termToSend = currentTerm;
            commitToSend = commitIndex;
            peersSnapshot = new ArrayList<>(this.peers);
        }

        for (String peer : peersSnapshot) {
            String body = String.format(
                    "{\"term\":%d, \"leaderId\":\"%s\", \"prevLogIndex\":%d, \"prevLogTerm\":%d, \"leaderCommit\":%d}",
                    termToSend, nodeId, 0, 0, commitToSend);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(peer + "/raft/rpc/appendentries"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                         // We could track peer health here
                    })
                    .exceptionally(e -> {
                        return null;
                    });
        }
    }

    public synchronized boolean requestVote(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
        if (term > currentTerm) {
            currentTerm = term;
            state = State.FOLLOWER;
            votedFor = null;
        }

        if (term < currentTerm) {
            return false;
        }

        if ((votedFor == null || votedFor.equals(candidateId))) {
            votedFor = candidateId;
            lastHeartbeatTime = System.currentTimeMillis();
            return true;
        }

        return false;
    }

    public synchronized boolean appendEntries(int term, String leaderId, int prevLogIndex, int prevLogTerm,
            List<LogEntry> entries, int leaderCommit) {
        if (term > currentTerm) {
            currentTerm = term;
            state = State.FOLLOWER;
            votedFor = null;
        }

        if (term < currentTerm) {
            return false;
        }

        this.leaderId = leaderId;
        this.state = State.FOLLOWER;
        this.lastHeartbeatTime = System.currentTimeMillis();

        if (configManager.isBootstrap()) {
            configManager.setBootstrap(false);
        }

        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.size());
        }

        return true;
    }

    public synchronized boolean appendEntry(int term, String leaderId, Map<String, Object> command) {
        if (!appendEntries(term, leaderId, 0, 0, null, 0)) {
            return false;
        }

        if (command != null) {
            LOGGER.info("Applying replicated command: " + command);
            applyCommand(command);
        }
        return true;
    }

    private void applyCommand(Map<String, Object> command) {
        try {
            String op = (String) command.get("op");
            if (op == null) return;

            switch (op) {
                case "create_db": {
                    String db = (String) command.get("db");
                    String engine = (String) command.get("engine");
                    try {
                        store.createDatabase(db, engine);
                        store.createCollection(db, "_info");
                        store.createCollection(db, "_rules");
                    } catch (Exception e) {
                        // Ignore if exists
                    }
                    LOGGER.info("Replicated Create DB: " + db);
                    break;
                }
                case "cluster_register": {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nodeDoc = (Map<String, Object>) command.get("node");
                    String id = (String) nodeDoc.get("_id");
                    
                    try {
                        if (store.findByID("_system", "_clusternodes", id) != null) {
                            store.update("_system", "_clusternodes", id, nodeDoc);
                        } else {
                            store.save("_system", "_clusternodes", nodeDoc);
                        }
                    } catch (Exception e) {
                         LOGGER.warning("Error saving/updating node " + id + ": " + e.getMessage());
                    }
                    
                    LOGGER.info("Replicated Node Register: " + id);
                    loadPeersFromStore();
                    break;
                }
                case "cluster_deregister": {
                     String url = (String) command.get("url");
                     List<Map<String,Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
                     for(Map<String, Object> n : nodes) {
                         if (url.equals(n.get("url"))) {
                             String id = (String) (n.get("_id") != null ? n.get("_id") : n.get("id"));
                             store.delete("_system", "_clusternodes", id);
                             LOGGER.info("Replicated Node Deregister: " + url);
                         }
                     }
                    loadPeersFromStore();
                    break;
                }
                case "cluster_stop": {
                    String url = (String) command.get("url");
                    List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
                    for (Map<String, Object> n : nodes) {
                        if (url.equals(n.get("url"))) {
                            String id = (String) (n.get("_id") != null ? n.get("_id") : n.get("id"));
                            n.put("status", "INACTIVE");
                            store.update("_system", "_clusternodes", id, n);
                             LOGGER.info("Replicated Node Stop: " + url);
                        }
                    }
                    loadPeersFromStore();
                    break;
                }
                case "save": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> doc = (Map<String, Object>) command.get("doc");
                    // Ensure ID is present if passed in command, usually it is.
                    store.save(db, col, doc);
                    LOGGER.info("Replicated Save: " + db + "/" + col + " ID: " + doc.get("_id"));
                    break;
                }
                case "update": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    String id = (String) command.get("id");
                    @SuppressWarnings("unchecked")
                    Map<String, Object> doc = (Map<String, Object>) command.get("doc");
                    store.update(db, col, id, doc);
                    LOGGER.info("Replicated Update: " + db + "/" + col + " ID: " + id);
                    break;
                }
                case "delete": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    String id = (String) command.get("id");
                    store.delete(db, col, id);
                    LOGGER.info("Replicated Delete: " + db + "/" + col + " ID: " + id);
                    break;
                }
                 case "cluster_update_node": {
                     String id = (String) command.get("id");
                     @SuppressWarnings("unchecked")
                     Map<String, Object> data = (Map<String, Object>) command.get("data");
                     store.update("_system", "_clusternodes", id, data);
                     loadPeersFromStore();
                     break;
                 }
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to apply command: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public synchronized boolean replicate(Map<String, Object> command) {
        if (state != State.LEADER) {
            return false;
        }
        log.add(new LogEntry(currentTerm, command));
        sendAppendEntries(command);
        return true;
    }

    // Helper to send command directly
    private void sendAppendEntries(Map<String, Object> command) {
        if (state != State.LEADER)
            return;
        try {
            String cmdJson = mapper.writeValueAsString(command);
            String body = String.format("{\"term\":%d, \"leaderId\":\"%s\", \"leaderCommit\":%d, \"command\": %s}",
                    currentTerm, nodeId, commitIndex, cmdJson);
            
            List<String> peersSnapshot = new ArrayList<>(peers);
            for (String peer : peersSnapshot) {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(peer + "/raft/rpc/appendentries"))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
            }
        } catch (Exception e) {
            LOGGER.severe("Error sending AppendEntries: " + e.getMessage());
        }
    }

    // --- Cluster Management API ---

    public void registerNode(String url, String description) {
        synchronized (this) {
            if (state != State.LEADER) {
                throw new IllegalStateException("Not Leader");
            }

            // Check if node already exists
            String existingId = null;
            try {
                List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
                for (Map<String, Object> n : nodes) {
                    if (url.equals(n.get("url"))) {
                        existingId = (String) (n.get("_id") != null ? n.get("_id") : n.get("id"));
                        break;
                    }
                }
            } catch (Exception e) {
                LOGGER.warning("Error checking for existing node: " + e.getMessage());
            }

            if (existingId != null) {
                LOGGER.info("Node already registered: " + url + " (ID: " + existingId + "). Updating status.");
                Map<String, Object> data = new HashMap<>();
                data.put("status", "ACTIVE");
                if (description != null && !description.isEmpty()) {
                    data.put("description", description);
                }
                
                Map<String, Object> cmd = new HashMap<>();
                cmd.put("op", "cluster_update_node");
                cmd.put("id", existingId);
                cmd.put("data", data);
                
                replicate(cmd);
                applyCommand(cmd);
                replicateStateTo(url); // Replicate state even if updating
                return;
            }

            // New Node
            String nodeId = "node-" + Math.abs(url.hashCode());
            Map<String, Object> nodeDoc = new HashMap<>();
            nodeDoc.put("_id", nodeId);
            nodeDoc.put("url", url);
            nodeDoc.put("role", "FOLLOWER");
            nodeDoc.put("status", "ACTIVE");
            if (description != null && !description.isEmpty()) {
                nodeDoc.put("description", description);
            }

            LOGGER.info("Registering node with doc: " + nodeDoc);

            Map<String, Object> cmd = new HashMap<>();
            cmd.put("op", "cluster_register");
            cmd.put("node", nodeDoc);

            replicate(cmd);
            LOGGER.info("Replicated registration for " + url);
            applyCommand(cmd); // Apply locally
            LOGGER.info("Applied registration command locally.");
        }
        
        // Send snapshot to new node
        replicateStateTo(url);
    }

    public void deregisterNode(String url) {
        synchronized (this) {
             if (state != State.LEADER) {
                throw new IllegalStateException("Not Leader");
            }
             
            Map<String, Object> cmd = new HashMap<>();
            cmd.put("op", "cluster_deregister");
            cmd.put("url", url);
            
            replicate(cmd);
            applyCommand(cmd);
        }
    }
    
    public void stopNode(String nodeIdOrUrl) {
         if (state != State.LEADER) {
            throw new IllegalStateException("Only Leader can stop nodes");
        }
        
        String targetUrl = null;
        try {
            List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
            for (Map<String, Object> n : nodes) {
                String id = (String) (n.get("_id") != null ? n.get("_id") : n.get("id"));
                String u = (String) n.get("url");
                if ((id != null && id.equals(nodeIdOrUrl)) || (u != null && u.equals(nodeIdOrUrl))) {
                    targetUrl = u;
                    break;
                }
            }
        } catch(Exception e) {
            LOGGER.severe("Error searching for node to stop: " + e.getMessage());
        }

        if (targetUrl == null) {
            throw new IllegalArgumentException("Node not found: " + nodeIdOrUrl);
        }

        LOGGER.info("Sending STOP command to " + targetUrl);
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(targetUrl + "/raft/rpc/stop"))
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .thenAccept(res -> LOGGER.info("Stop sent to " + nodeIdOrUrl + ": " + res.statusCode()));
        
        Map<String, Object> cmd = new HashMap<>();
        cmd.put("op", "cluster_stop");
        cmd.put("url", targetUrl);
        replicate(cmd);
        applyCommand(cmd);
    }

    public synchronized List<Map<String, Object>> getClusterStatus() {
        try {
            return store.query("_system", "_clusternodes", null, 1000, 0);
        } catch(Exception e) {
            LOGGER.severe("Failed to get cluster status: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void replicateStateTo(String url) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            LOGGER.info("Starting background state replication (snapshot) to " + url);
            File snapshot = createSnapshot();
            if (snapshot == null)
                return;

            try {
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url + "/raft/rpc/installsnapshot"))
                        .header("Content-Type", "application/octet-stream")
                        .POST(HttpRequest.BodyPublishers.ofFile(snapshot.toPath()))
                        .build();

                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                        .thenAccept(res -> {
                            LOGGER.info("Snapshot sent to " + url + " status: " + res.statusCode());
                            if (res.statusCode() == 200) {
                                if (!snapshot.delete()) {
                                    snapshot.deleteOnExit();
                                }
                            }
                        });
            } catch (Exception e) {
                LOGGER.severe("Failed to send snapshot: " + e.getMessage());
            }
        });
    }

    private File createSnapshot() {
        try {
            String dataDir = (String) configManager.get("DataDir");
            if (dataDir == null)
                dataDir = "data";
            File snapshotFile = File.createTempFile("snapshot", ".zip");
            List<String> excludes = List.of("cluster.json", "config.json", "raft", "temp", "tmp");
            io.jettra.core.util.ZipUtils.zipDirectory(new File(dataDir).toPath(), snapshotFile.toPath(), excludes);
            return snapshotFile;
        } catch (IOException e) {
            LOGGER.severe("Failed to create snapshot: " + e.getMessage());
            return null;
        }
    }

    public void installSnapshot(java.io.InputStream is) throws IOException {
        String dataDir = (String) configManager.get("DataDir");
        if (dataDir == null)
            dataDir = "data";

        File tempZip = File.createTempFile("install", ".zip");
        java.nio.file.Files.copy(is, tempZip.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        io.jettra.core.util.ZipUtils.unzip(tempZip.toPath(), new File(dataDir).toPath());

        if (!tempZip.delete()) {
            tempZip.deleteOnExit();
        }

        try {
            store.reload();
            loadPeersFromStore(); // Refresh peers from new data
        } catch (Exception e) {
            LOGGER.severe("Failed to reload store after snapshot: " + e.getMessage());
        }
        LOGGER.info("Snapshot installed successfully.");
        this.lastHeartbeatTime = System.currentTimeMillis();
        
        // Force step down to Follower as we accepted a snapshot from Leader
        synchronized(this) {
            this.state = State.FOLLOWER;
            this.votedFor = null;
            // Term logic? If we don't know the term, 0 might cause rejection of future heartbeats unless we accept any term > current.
            // But usually snapshot includes term.
            // For now, reset to 0 to be safe, or keep it if we persist it?
            // Resetting to 0 ensures we accept any Leader with Term >= 1.
            this.currentTerm = 0; 
        }
    }
    
    public static class LogEntry {
        public int term;
        public Map<String, Object> command;

        public LogEntry(int term, Map<String, Object> command) {
            this.term = term;
            this.command = command;
        }
    }
}
