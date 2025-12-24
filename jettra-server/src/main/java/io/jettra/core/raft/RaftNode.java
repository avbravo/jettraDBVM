package io.jettra.core.raft;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
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

import io.jettra.core.storage.IndexEngine;

/**
 * Simplified Raft implementation for JettraDBVM.
 * Handles Leader Election, Log storage, and Dynamic Clustering via
 * _clusternodes collection.
 */
public class RaftNode {
    private static final Logger LOGGER = Logger.getLogger(RaftNode.class.getName());

    public java.net.http.HttpClient getHttpClientProxy() {
        return httpClient;
    }

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
    private boolean federatedSyncStatus = false;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private long lastHeartbeatTime;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(2000))
            .build();

    // Simplified timeouts (in ms)
    // Higher timeouts to prevent accidental elections in stable clusters
    private static final int ELECTION_TIMEOUT_MIN = 6000;
    private static final int ELECTION_TIMEOUT_MAX = 12000;
    private static final int HEARTBEAT_INTERVAL = 1500;

    private final io.jettra.core.config.ConfigManager configManager;
    private final io.jettra.core.storage.DocumentStore store;
    private final IndexEngine indexer;

    // Cache for node statuses to avoid DB lookups in heartbeats
    private final Map<String, String> nodeStatusCache = new java.util.concurrent.ConcurrentHashMap<>();
    private final Map<String, Integer> nodeFailureCount = new java.util.concurrent.ConcurrentHashMap<>();
    private static final int MAX_FAILURE_COUNT = 5;

    public RaftNode(String nodeId, List<String> peers, io.jettra.core.config.ConfigManager configManager,
            io.jettra.core.storage.DocumentStore store, IndexEngine indexer) {
        this.nodeId = nodeId;
        this.configManager = configManager;
        this.store = store;
        this.indexer = indexer;

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

                    if (!isFederatedMode()) {
                        LOGGER.info("Bootstrap node initialized in _clusternodes. Becoming LEADER.");
                        becomeLeader();
                    } else {
                        LOGGER.info("Bootstrap node initialized in _clusternodes. Waiting for Federated Server for role.");
                    }
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
        if (isBootstrap && !isFederatedMode()) {
            if (this.peers.isEmpty()) {
                LOGGER.info("Bootstrap node with no peers (or single node). Forcing LEADER role.");
                becomeLeader();
            } else {
                long delay = 500; // Default delay
                if (nodeId.equals("node-1")) {
                    delay = 50; // Node 1 starts almost immediately
                } else {
                    int idNum = 0;
                    try {
                        String numericPart = nodeId.replaceAll("[^0-9]", "");
                        if (!numericPart.isEmpty())
                            idNum = Integer.parseInt(numericPart);
                    } catch (Exception e) {
                    }
                    delay = 300 + ((long) (idNum % 100) * 100);
                }

                LOGGER.log(Level.INFO,
                        "Bootstrap node with peers. Cautiously checking for leader before election in {0}ms.", delay);
                scheduler.schedule(() -> {
                    if (this.leaderId == null && this.state != State.LEADER) {
                        LOGGER.info("No leader detected after bootstrap delay. Starting election to assert priority.");
                        startElection();
                    } else {
                        LOGGER.log(Level.INFO, "Leader already present ({0}), bootstrap node will stay as FOLLOWER.",
                                this.leaderId);
                    }
                }, delay, TimeUnit.MILLISECONDS);
            }
        } else if (isFederatedMode()) {
            LOGGER.info("Federated mode active. Waiting for Federated Server for initial role assignment.");
        }

        this.currentTerm = 0;
        if (this.state != State.LEADER) {
            this.state = State.FOLLOWER;
        }

        startFederatedSync();

        this.lastHeartbeatTime = System.currentTimeMillis();

        // Start single tick loop for both heartbeats and election timeouts
        scheduler.scheduleAtFixedRate(this::tick, 1000, 200, TimeUnit.MILLISECONDS);
    }

    private void startFederatedSync() {
        List<String> fedServers = (List<String>) configManager.getOrDefault("FederatedServers",
                Collections.emptyList());
        if (!fedServers.isEmpty()) {
            LOGGER.info("Federated mode active. Starting registration and heartbeat sync.");
            scheduler.scheduleAtFixedRate(this::syncWithFederated, 0, 5, TimeUnit.SECONDS);
        }
    }

    private void syncWithFederated() {
        try {
            List<String> fedServers = (List<String>) configManager.getOrDefault("FederatedServers",
                    Collections.emptyList());
            if (fedServers.isEmpty())
                return;

            // Attempt to sync with servers until one succeeds
            syncWithFirstAvailableFederated(fedServers, 0);
        } catch (Exception e) {
            LOGGER.warning("Federated sync error: " + e.getMessage());
        }
    }

    private void syncWithFirstAvailableFederated(List<String> fedServers, int index) {
        if (index >= fedServers.size()) {
            return;
        }

        String fedUrl = fedServers.get(index);
        try {
            Map<String, String> data = new HashMap<>();
            data.put("nodeId", nodeId);
            data.put("url", "http://localhost:" + configManager.get("Port"));

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(fedUrl + "/federated/register"))
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data)))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofMillis(2000))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(res -> {
                        if (res.statusCode() == 200) {
                            this.federatedSyncStatus = true;
                            processFederatedResponse(res.body());
                        } else {
                            LOGGER.warning("Failed to sync with Federated Server " + fedUrl + ". Trying next...");
                            syncWithFirstAvailableFederated(fedServers, index + 1);
                        }
                    })
                    .exceptionally(e -> {
                        LOGGER.warning("Error connecting to Federated Server " + fedUrl + ": " + e.getMessage());
                        syncWithFirstAvailableFederated(fedServers, index + 1);
                        return null;
                    });
        } catch (Exception e) {
            syncWithFirstAvailableFederated(fedServers, index + 1);
        }
    }

    private void processFederatedResponse(String body) {
        try {
            Map<String, Object> status = mapper.readValue(body, Map.class);
            String fedLeaderId = (String) status.get("leaderId");
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) status.get("nodes");

            if (fedLeaderId != null) {
                if (!fedLeaderId.equals(leaderId)) {
                    if (!fedLeaderId.equals(nodeId)) {
                        LOGGER.info("Federated server reports NEW LEADER: " + fedLeaderId
                                + ". Updating local view.");
                        leaderId = fedLeaderId;
                        if (state == State.LEADER) {
                            state = State.FOLLOWER;
                            updateSelfRoleInDB("FOLLOWER");
                        }
                    } else {
                        if (state != State.LEADER) {
                            LOGGER.info("Federated server reports ME as LEADER. Transitioning.");
                            becomeLeader();
                        }
                    }
                } else if (fedLeaderId.equals(nodeId) && state != State.LEADER) {
                    LOGGER.info(
                            "Federated server reports ME as LEADER and leaderId matches but local state is " + state
                                    + ". Recovering leadership.");
                    becomeLeader();
                }
            }

            if (nodes != null) {
                for (Map<String, Object> node : nodes) {
                    String id = (String) node.get("id");
                    String url = (String) node.get("url");
                    String nstatus = (String) node.get("status");

                    Map<String, Object> doc = new HashMap<>();
                    doc.put("_id", id);
                    doc.put("url", url);
                    doc.put("status", nstatus);
                    doc.put("role", id.equals(fedLeaderId) ? "LEADER" : "FOLLOWER");

                    try {
                        if (store.findByID("_system", "_clusternodes", id) != null) {
                            store.update("_system", "_clusternodes", id, doc);
                        } else {
                            store.save("_system", "_clusternodes", doc);
                        }
                    } catch (Exception e) {
                    }
                }
                loadPeersFromStore();
            }
        } catch (Exception e) {
            LOGGER.warning("Error parsing federated response: " + e.getMessage());
        }
    }

    private final java.util.concurrent.ConcurrentHashMap<String, String> peerUrlToId = new java.util.concurrent.ConcurrentHashMap<>();

    private void loadPeersFromStore() {
        this.peers.clear();
        this.peerUrlToId.clear();
        try {
            List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
            boolean foundSelf = false;
            Set<String> uniquePeers = new HashSet<>();
            for (Map<String, Object> node : nodes) {
                String id = (String) (node.get("_id") != null ? node.get("_id") : node.get("id"));
                String url = (String) node.get("url");

                if (id != null && url != null) {
                    if (id.equals(this.nodeId)) {
                        foundSelf = true;
                    }
                    peerUrlToId.put(url, id);
                    String status = (String) node.getOrDefault("status", "ACTIVE");
                    nodeStatusCache.put(id, status);

                    // Filter out itself by ID
                    if (!id.equals(this.nodeId)) {
                        uniquePeers.add(url);
                    }
                }
            }
            this.peers.addAll(uniquePeers);

            if (!foundSelf && !nodes.isEmpty()) {
                LOGGER.log(Level.WARNING,
                        "This node ({0}) is not present in _clusternodes! It has been removed. Disabling cluster activities.",
                        nodeId);
                this.state = State.FOLLOWER;
                this.votedFor = null;
                this.leaderId = null;
                this.peers.clear();
                this.peerUrlToId.clear();
            }

            LOGGER.log(Level.FINE, "Loaded peers from storage: {0} with statuses: {1}",
                    new Object[] { peers, nodeStatusCache });
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to load peers from storage: {0}", e.getMessage());
        }
    }

    public synchronized void start() {
        LOGGER.log(Level.INFO, "RaftNode started: {0} Peers: {1}", new Object[] { nodeId, peers });
    }

    public synchronized void promoteToLeader() {
        if (state != State.LEADER) {
            LOGGER.info("Promotion request received. Transitioning to LEADER.");
            becomeLeader();
        }
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
        synchronized (this) {
            if (state == State.LEADER)
                return;
        }

        // Fix: Do not start election if we are not initialized in the cluster storage
        try {
            int count = store.count("_system", "_clusternodes");
            if (count == 0) {
                return;
            }
        } catch (Exception e) {
        }

        long last;
        synchronized (this) {
            last = lastHeartbeatTime;
        }

        long now = System.currentTimeMillis();
        long timeout = ELECTION_TIMEOUT_MIN + (long) (Math.random() * (ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN));
        if (now - last > timeout) {
            if (isFederatedMode() && !federatedSyncStatus) {
                LOGGER.fine("Waiting for Federated Server before starting election.");
                return;
            }
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
            try {
                if (store.count("_system", "_clusternodes") > 0) {
                    becomeLeader();
                } else {
                    state = State.FOLLOWER;
                }
            } catch (Exception e) {
                state = State.FOLLOWER;
            }
            return;
        }

        String body = String.format("{\"term\":%d, \"candidateId\":\"%s\", \"lastLogIndex\":%d, \"lastLogTerm\":%d}",
                termForElection, nodeId, log.size(), 0);

        List<String> currentPeers = new ArrayList<>(peers);
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
                                        if (isFederatedMode() && !federatedSyncStatus) {
                                            LOGGER.warning("Majority reached but federated sync not yet achieved. Postponing leadership.");
                                        } else {
                                            becomeLeader();
                                        }
                                    }
                                }
                            } catch (Exception e) {
                            }
                        }
                    });
        }
    }

    private synchronized void becomeLeader() {
        if (state == State.LEADER)
            return;
        LOGGER.log(Level.INFO, "{0} becoming LEADER for term {1}",
                new Object[] { nodeId, currentTerm });
        state = State.LEADER;
        leaderId = nodeId;
        lastHeartbeatTime = System.currentTimeMillis();
        updateSelfRoleInDB("LEADER");
    }

    private void updateSelfRoleInDB(String role) {
        try {
            Map<String, Object> self = store.findByID("_system", "_clusternodes", nodeId);
            if (self != null) {
                if (!role.equals(self.get("role"))) {
                    self.put("role", role);
                    self.put("status", "ACTIVE");
                    store.update("_system", "_clusternodes", nodeId, self);

                    Map<String, Object> cmd = new HashMap<>();
                    cmd.put("op", "cluster_update_node");
                    cmd.put("id", nodeId);
                    cmd.put("data", self);
                    replicate(cmd);
                }
            }
        } catch (Exception e) {
        }
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
            String peerId = peerUrlToId.get(peer);
            boolean isPaused = false;

            if (peerId != null) {
                String status = nodeStatusCache.get(peerId);
                if ("PAUSED".equals(status)) {
                    isPaused = true;
                }
            }

            if (isPaused)
                continue;

            String body = String.format(
                    "{\"term\":%d, \"leaderId\":\"%s\", \"prevLogIndex\":%d, \"prevLogTerm\":%d, \"leaderCommit\":%d}",
                    termToSend, nodeId, 0, 0, commitToSend);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(peer + "/raft/rpc/appendentries"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(java.time.Duration.ofMillis(1000))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() == 200) {
                            markNodeActive(peer);
                        } else {
                            markNodeInactive(peer);
                        }
                    })
                    .exceptionally(e -> {
                        markNodeInactive(peer);
                        return null;
                    });
        }
    }

    private void markNodeActive(String url) {
        String id = peerUrlToId.get(url);
        if (id != null)
            nodeFailureCount.put(id, 0);
        updateNodeStatus(url, "ACTIVE", "INACTIVE");
    }

    private void markNodeInactive(String url) {
        String id = peerUrlToId.get(url);
        if (id != null) {
            int count = nodeFailureCount.getOrDefault(id, 0) + 1;
            nodeFailureCount.put(id, count);
            if (count < MAX_FAILURE_COUNT) {
                return;
            }
        }
        updateNodeStatus(url, "INACTIVE", null);
    }

    private void updateNodeStatus(String url, String newStatus, String currentStatusConstraint) {
        String id = peerUrlToId.get(url);
        if (id == null)
            return;

        try {
            Map<String, Object> node = store.findByID("_system", "_clusternodes", id);
            if (node != null) {
                String current = (String) node.get("status");
                if (currentStatusConstraint != null && !currentStatusConstraint.equals(current)) {
                    return;
                }

                if (newStatus.equals(current)) {
                    nodeStatusCache.put(id, newStatus);
                    return;
                }

                LOGGER.log(Level.INFO, "Updating node status for {0} from {1} to {2}",
                        new Object[] { url, current, newStatus });
                node.put("status", newStatus);
                nodeStatusCache.put(id, newStatus);

                store.update("_system", "_clusternodes", id, node);

                Map<String, Object> cmd = new HashMap<>();
                cmd.put("op", "cluster_update_node");
                cmd.put("id", id);
                cmd.put("data", node);
                replicate(cmd);

                // If coming back from INACTIVE, trigger full state sync to catch up
                if ("ACTIVE".equals(newStatus) && "INACTIVE".equals(current)) {
                    LOGGER.info("Node " + url + " transitioned from INACTIVE to ACTIVE. Triggering catch-up snapshot.");
                    replicateStateTo(url);
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error updating node status for " + url + ": " + e.getMessage());
        }
    }

    private void tick() {
        synchronized (this) {
            if (peers.isEmpty() && !nodeId.equals("node-1") && !configManager.isBootstrap()) {
                try {
                    Map<String, Object> self = store.findByID("_system", "_clusternodes", nodeId);
                    if (self == null) {
                        return;
                    }
                } catch (Exception e) {
                }
            }
        }

        State currentState;
        synchronized (this) {
            currentState = this.state;
        }

        if (currentState == State.LEADER) {
            long now = System.currentTimeMillis();
            if (now - lastHeartbeatTime >= HEARTBEAT_INTERVAL) {
                lastHeartbeatTime = now;
                sendHeartbeats();
            }
        } else {
            checkElectionTimeout();
        }
    }

    public synchronized boolean requestVote(int term, String candidateId, int lastLogIndex, int lastLogTerm) {
        if (!isClusterMember(candidateId)) {
            LOGGER.log(Level.WARNING, "Ignoring RequestVote from non-member candidate: {0}", candidateId);
            return false;
        }

        if (term > currentTerm) {
            currentTerm = term;
            if (state != State.FOLLOWER) {
                LOGGER.log(Level.INFO, "Term increased to {0}. Node {1} becoming FOLLOWER via RequestVote.",
                        new Object[] { term, nodeId });
                state = State.FOLLOWER;
                votedFor = null;
                leaderId = null;
                updateSelfRoleInDB("FOLLOWER");
            }
            lastHeartbeatTime = System.currentTimeMillis();
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
        if (!isClusterMember(leaderId)) {
            LOGGER.log(Level.WARNING, "Ignoring AppendEntries from non-member leader: {0}", leaderId);
            return false;
        }

        if (term > currentTerm) {
            currentTerm = term;
            if (state != State.FOLLOWER) {
                LOGGER.log(Level.INFO, "Term increased to {0}. Node {1} becoming FOLLOWER via AppendEntries.",
                        new Object[] { term, nodeId });
                state = State.FOLLOWER;
                votedFor = null;
                updateSelfRoleInDB("FOLLOWER");
            }
            lastHeartbeatTime = System.currentTimeMillis();
        }

        if (term < currentTerm) {
            return false;
        }

        this.leaderId = leaderId;
        this.lastHeartbeatTime = System.currentTimeMillis();

        if (!leaderId.equals(nodeId) && state != State.FOLLOWER) {
            LOGGER.log(Level.INFO, "Node {0} becoming FOLLOWER of {1} for term {2}",
                    new Object[] { nodeId, leaderId, term });
            this.state = State.FOLLOWER;
            updateSelfRoleInDB("FOLLOWER");
        }

        if (leaderCommit > commitIndex) {
            commitIndex = Math.min(leaderCommit, log.size());
        }

        // Add entries to log and apply
        if (entries != null && !entries.isEmpty()) {
            for (LogEntry entry : entries) {
                log.add(entry);
                if (entry.command != null) {
                    LOGGER.info("Applying entry from AppendEntries log: " + entry.command.get("op"));
                    applyCommand(entry.command);
                }
            }
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
            if (op == null)
                return;

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
                    List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
                    for (Map<String, Object> n : nodes) {
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
                case "create_collection": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    try {
                        store.createCollection(db, col);
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Create Collection: " + db + "/" + col);
                    break;
                }
                case "delete_collection": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    try {
                        store.deleteCollection(db, col);
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Delete Collection: " + db + "/" + col);
                    break;
                }
                case "rename_collection": {
                    String db = (String) command.get("db");
                    String oldName = (String) command.get("oldName");
                    String newName = (String) command.get("newName");
                    try {
                        store.renameCollection(db, oldName, newName);
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Rename Collection: " + db + "/" + oldName + " -> " + newName);
                    break;
                }
                case "delete_db": {
                    String db = (String) command.get("db");
                    try {
                        store.deleteDatabase(db);
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Delete DB: " + db);
                    break;
                }
                case "rename_db": {
                    String oldName = (String) command.get("oldName");
                    String newName = (String) command.get("newName");
                    try {
                        store.renameDatabase(oldName, newName);
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Rename DB: " + oldName + " -> " + newName);
                    break;
                }
                case "create_index": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    List<String> fields = (List<String>) command.get("fields");
                    boolean unique = (boolean) command.get("unique");
                    boolean sequential = (boolean) command.get("sequential");
                    try {
                        if (indexer != null) {
                            indexer.createIndex(db, col, fields, unique, sequential);
                        }
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Create Index: " + db + "/" + col);
                    break;
                }
                case "delete_index": {
                    String db = (String) command.get("db");
                    String col = (String) command.get("col");
                    List<String> fields = (List<String>) command.get("fields");
                    try {
                        if (indexer != null) {
                            indexer.deleteIndex(db, col, fields);
                        }
                    } catch (Exception e) {
                    }
                    LOGGER.info("Replicated Delete Index: " + db + "/" + col);
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
        LogEntry entry = new LogEntry(currentTerm, command);
        log.add(entry);
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
                try {
                    // Check status before sending data
                    String id = peerUrlToId.get(peer);
                    if (id != null) {
                        Map<String, Object> node = store.findByID("_system", "_clusternodes", id);
                        if (node != null) {
                            String status = (String) node.get("status");
                            // Skip sending commands to PAUSED or INACTIVE nodes
                            if ("PAUSED".equals(status) || "INACTIVE".equals(status))
                                continue;
                        }
                    }

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(peer + "/raft/rpc/appendentries"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();
                    httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString());
                } catch (Exception e) {
                    LOGGER.warning("Error sending AppendEntries to " + peer + ": " + e.getMessage());
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error formatting AppendEntries body: " + e.getMessage());
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

            applyCommand(cmd); // Apply locally first to update 'peers' list
            replicate(cmd); // Then replicate to others
            LOGGER.info("Registered and replicated node: " + url);
        }

        // Send snapshot to new node (background)
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

        String targetUrl = findPeerUrl(nodeIdOrUrl);
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

        // Also mark as INACTIVE
        updateNodeStatus(targetUrl, "INACTIVE", null);
    }

    public void pauseNode(String nodeIdOrUrl) {
        if (state != State.LEADER) {
            throw new IllegalStateException("Only Leader can pause nodes");
        }

        String targetUrl = findPeerUrl(nodeIdOrUrl);
        if (targetUrl == null) {
            throw new IllegalArgumentException("Node not found: " + nodeIdOrUrl);
        }

        LOGGER.info("Pausing node (stopping data send): " + targetUrl);
        updateNodeStatus(targetUrl, "PAUSED", null);
    }

    public void resumeNode(String nodeIdOrUrl) {
        if (state != State.LEADER) {
            throw new IllegalStateException("Only Leader can resume nodes");
        }

        String targetUrl = findPeerUrl(nodeIdOrUrl);
        if (targetUrl == null) {
            throw new IllegalArgumentException("Node not found: " + nodeIdOrUrl);
        }

        LOGGER.info("Resuming node: " + targetUrl);
        updateNodeStatus(targetUrl, "ACTIVE", null);

        // Immediately trigger a snapshot or replication?
        // Heartbeats will resume and fix term/commit index.
        replicateStateTo(targetUrl);
    }

    private String findPeerUrl(String key) {
        try {
            List<Map<String, Object>> nodes = store.query("_system", "_clusternodes", null, 1000, 0);
            for (Map<String, Object> n : nodes) {
                String id = (String) (n.get("_id") != null ? n.get("_id") : n.get("id"));
                String u = (String) n.get("url");
                if ((id != null && id.equals(key)) || (u != null && u.equals(key))) {
                    return u;
                }
            }
        } catch (Exception e) {
            LOGGER.severe("Error searching for node: " + e.getMessage());
        }
        return null;
    }

    private boolean isClusterMember(String id) {
        if (id == null)
            return false;
        if (id.equals(this.nodeId))
            return true;
        return peerUrlToId.containsValue(id);
    }

    public synchronized List<Map<String, Object>> getClusterStatus() {
        try {
            return store.query("_system", "_clusternodes", null, 1000, 0);
        } catch (Exception e) {
            LOGGER.severe("Failed to get cluster status: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    private void replicateStateTo(String url) {
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            // Wait a bit to ensure the target node's web server is up if this was a fresh
            // registration
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
            }

            int maxRetries = 3;
            int attempt = 0;
            boolean success = false;

            while (attempt < maxRetries && !success) {
                attempt++;
                LOGGER.info("State replication attempt " + attempt + " for " + url);

                File snapshot = createSnapshot();
                if (snapshot == null)
                    return;
                long size = snapshot.length();
                LOGGER.info("Created snapshot size: " + size + " bytes for " + url);

                try {
                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(url + "/raft/rpc/installsnapshot"))
                            .header("Content-Type", "application/octet-stream")
                            .header("X-Snapshot-Size", String.valueOf(size))
                            .POST(HttpRequest.BodyPublishers.ofFile(snapshot.toPath()))
                            .timeout(java.time.Duration.ofMinutes(5)) // Allow time for large snapshots
                            .build();

                    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                    if (response.statusCode() == 200) {
                        LOGGER.info("Successfully replicated state to " + url + " on attempt " + attempt);
                        success = true;
                    } else {
                        LOGGER.warning("Failed to replicate state to " + url + ". Status: " + response.statusCode()
                                + " Body: " + response.body());
                        if (attempt < maxRetries)
                            Thread.sleep(3000 * attempt);
                    }
                } catch (Exception e) {
                    LOGGER.severe("Error sending snapshot to " + url + " (attempt " + attempt + "): " + e.getMessage());
                    try {
                        if (attempt < maxRetries)
                            Thread.sleep(3000 * attempt);
                    } catch (InterruptedException ie) {
                    }
                } finally {
                    if (snapshot != null && snapshot.exists()) {
                        if (!snapshot.delete())
                            snapshot.deleteOnExit();
                    }
                }
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
        synchronized (this) {
            this.state = State.FOLLOWER;
            this.votedFor = null;
            // Removed currentTerm = 0; Keep current term to avoid being disruptive.
            // Heartbeats from Leader will keep us synced or update us if we are behind.
            LOGGER.info("Accepted snapshot. Ensuring Node " + nodeId + " is FOLLOWER.");
        }
    }

    public boolean isFederatedMode() {
        List<String> fedServers = (List<String>) configManager.getOrDefault("FederatedServers", Collections.emptyList());
        return !fedServers.isEmpty();
    }

    public synchronized boolean hasLeader() {
        return leaderId != null;
    }

    public synchronized boolean isFederatedSynced() {
        return federatedSyncStatus;
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
