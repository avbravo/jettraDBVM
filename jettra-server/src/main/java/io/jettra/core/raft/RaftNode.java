package io.jettra.core.raft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;

/**
 * Core Raft Node implementation.
 */
public class RaftNode {
    private static final Logger LOGGER = Logger.getLogger(RaftNode.class.getName());

    public enum State {
        FOLLOWER, CANDIDATE, LEADER
    }

    private final String nodeId;
    private final RaftLog log;
    private final Map<String, String> peerUrls; // nodeId -> url

    // Persistent state
    private long currentTerm = 0;
    private String votedFor = null;

    // Volatile state
    private State state = State.FOLLOWER;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);
    private long lastHeartbeatTime;
    
    // Leader state
    private final Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    private final Map<String, Long> matchIndex = new ConcurrentHashMap<>();

    // Configuration
    private final int ELECTION_TIMEOUT_MIN = 1500; // ms
    private final int ELECTION_TIMEOUT_MAX = 3000; // ms
    private final int HEARTBEAT_INTERVAL = 500; // ms

    private final boolean bootstrap;
    private final io.jettra.core.storage.DocumentStore store;
    
    // Constants for Peer Persistence
    private static final String SYSTEM_DB = "_system";
    private static final String CLUSTER_COL = "_cluster";

    public RaftNode(String nodeId, RaftLog log, io.jettra.core.storage.DocumentStore store, boolean bootstrap) {
        this.nodeId = nodeId;
        this.log = log;
        this.store = store;
        this.peerUrls = new ConcurrentHashMap<>(); // Load later
        this.bootstrap = bootstrap;
        this.lastHeartbeatTime = System.currentTimeMillis();
    }

    public void start() {
        LOGGER.info("Starting Raft Node: " + nodeId);
        loadPeers();
        resetElectionTimer();

        // If bootstrap is true and we are the only node (or just forcing start as leader for cluster init)
        if (bootstrap && log.getLastLogIndex() == 0 && peerUrls.isEmpty()) {
            LOGGER.info("Bootstrap mode detected. Becoming Leader.");
            becomeLeader();
        }
        
        // Start detailed ticker
        scheduler.scheduleAtFixedRate(this::tick, 100, 100, TimeUnit.MILLISECONDS);
    }
    
    private void loadPeers() {
        try {
             // We assume peers are stored in _system/_cluster as documents with "type": "peer"
             // Or maybe we can just query all docs in _cluster and filter?
             // Based on BootstrapManager, _cluster has a generic cluster info doc.
             // We will add peer docs there: { "type": "peer", "nodeId": "...", "url": "..." }
             
             Map<String, Object> filter = new java.util.HashMap<>();
             filter.put("type", "peer");
             
             // store.query might need exact match options or we loop all if filter not supported fully yet?
             // Assuming query supports it.
             List<Map<String, Object>> docs = store.query(SYSTEM_DB, CLUSTER_COL, filter, 100, 0);
             for (Map<String, Object> doc : docs) {
                 String pid = (String) doc.get("nodeId");
                 String purl = (String) doc.get("url");
                 if (pid != null && purl != null && !pid.equals(nodeId)) {
                     peerUrls.put(pid, purl);
                 }
             }
             LOGGER.info("Loaded " + peerUrls.size() + " peers from storage.");
        } catch (Exception e) {
            LOGGER.warning("Failed to load peers: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private void tick() {
        if (state == State.LEADER) {
            sendHeartbeats();
        } else {
            checkElectionTimeout();
        }
    }

    private void checkElectionTimeout() {
        long timeout = ELECTION_TIMEOUT_MIN + new Random().nextInt(ELECTION_TIMEOUT_MAX - ELECTION_TIMEOUT_MIN);
        if (System.currentTimeMillis() - lastHeartbeatTime > timeout) {
            startElection();
        }
    }

    private synchronized void startElection() {
        if (state == State.LEADER) return;
        
        LOGGER.info("Starting election for term " + (currentTerm + 1));
        state = State.CANDIDATE;
        currentTerm++;
        votedFor = nodeId;
        lastHeartbeatTime = System.currentTimeMillis();

        AtomicLong votes = new AtomicLong(1); // Vote for self
        int quorum = (peerUrls.size() + 1) / 2 + 1;
        
        // Request votes from peers
        for (Map.Entry<String, String> peer : peerUrls.entrySet()) {
            if (peer.getKey().equals(nodeId)) continue;
            
            requestVoteAsync(peer.getValue(), currentTerm, nodeId, log.getLastLogIndex(), log.getLastLogTerm())
                .thenAccept(granted -> {
                    if (granted) {
                        long v = votes.incrementAndGet();
                        if (v >= quorum && state == State.CANDIDATE) {
                            becomeLeader();
                        }
                    }
                });
        }
        
        if (peerUrls.isEmpty()) {
             becomeLeader();
        }
    }
    
    private void becomeLeader() {
        LOGGER.info("Becoming LEADER for term " + currentTerm);
        state = State.LEADER;
        // Re-init nextIndex/matchIndex
        for (String peerId : peerUrls.keySet()) {
            nextIndex.put(peerId, log.getLastLogIndex() + 1);
            matchIndex.put(peerId, 0L);
        }
        sendHeartbeats();
    }
    
    private void resetElectionTimer() {
        lastHeartbeatTime = System.currentTimeMillis();
    }

    // --- RPC Handlers ---

    public synchronized RequestVoteResponse handleRequestVote(long term, String candidateId, long lastLogIndex, long lastLogTerm) {
        boolean voteGranted = false;
        
        if (term > currentTerm) {
            currentTerm = term;
            state = State.FOLLOWER;
            votedFor = null;
        }

        if (term < currentTerm) {
            return new RequestVoteResponse(currentTerm, false);
        }

        if ((votedFor == null || votedFor.equals(candidateId)) && isLogUpToDate(lastLogIndex, lastLogTerm)) {
            voteGranted = true;
            votedFor = candidateId;
            resetElectionTimer();
        }

        return new RequestVoteResponse(currentTerm, voteGranted);
    }

    private boolean isLogUpToDate(long lastLogIndex, long lastLogTerm) {
        long myLastTerm = log.getLastLogTerm();
        long myLastIndex = log.getLastLogIndex();
        
        if (lastLogTerm != myLastTerm) {
            return lastLogTerm > myLastTerm;
        }
        return lastLogIndex >= myLastIndex;
    }

    public synchronized AppendEntriesResponse handleAppendEntries(long term, String leaderId, long prevLogIndex, long prevLogTerm, List<RaftLog.LogEntry> entries, long leaderCommit) {
        if (term > currentTerm) {
            currentTerm = term;
            state = State.FOLLOWER;
            votedFor = null;
        }
        
        if (term < currentTerm) {
            return new AppendEntriesResponse(currentTerm, false);
        }
        
        resetElectionTimer();
        if (state != State.FOLLOWER) {
             state = State.FOLLOWER;
        }
        
        // 1. Reply false if log doesn't contain an entry at prevLogIndex whose term matches prevLogTerm
        if (prevLogIndex > 0) {
             RaftLog.LogEntry entry = log.getEntry(prevLogIndex);
             if (entry == null || entry.getTerm() != prevLogTerm) {
                 return new AppendEntriesResponse(currentTerm, false);
             }
        }
        
        // 2. If an existing entry conflicts with a new one (same index but different terms), delete the existing entry and all that follow it
        // 3. Append any new entries not already in the log
        for (RaftLog.LogEntry entry : entries) {
            RaftLog.LogEntry existing = log.getEntry(entry.getIndex());
            if (existing != null && existing.getTerm() != entry.getTerm()) {
                try {
                log.deleteFrom(existing.getIndex());
                } catch(Exception e) { e.printStackTrace(); }
            }
            try {
                log.append(entry);
            } catch(Exception e) { e.printStackTrace(); }
        }
        // TODO: Commit index logic (min(leaderCommit, lastNewEntry))
        
        return new AppendEntriesResponse(currentTerm, true);
    }

    // --- Networking ---
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private java.util.concurrent.CompletableFuture<Boolean> requestVoteAsync(String url, long term, String candidateId, long lastLogIndex, long lastLogTerm) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("term", term);
        body.put("candidateId", candidateId);
        body.put("lastLogIndex", lastLogIndex);
        body.put("lastLogTerm", lastLogTerm);

        return sendRequest(url + "/raft/vote", body)
            .thenApply(responseMap -> {
                 if (responseMap == null) return false;
                 // check term
                 long responseTerm = ((Number) responseMap.get("term")).longValue();
                 if (responseTerm > currentTerm) {
                     currentTerm = responseTerm;
                     state = State.FOLLOWER;
                     votedFor = null;
                 }
                 return (boolean) responseMap.get("voteGranted");
            })
            .exceptionally(e -> {
                LOGGER.warning("RequestVote failed to " + url + ": " + e.getMessage());
                return false;
            });
    }
    
    private void sendHeartbeats() {
        for (Map.Entry<String, String> peer : peerUrls.entrySet()) {
            String peerId = peer.getKey();
            String url = peer.getValue();
            
            long prevLogIndex = nextIndex.getOrDefault(peerId, log.getLastLogIndex() + 1) - 1;
            long prevLogTerm = 0;
            if (prevLogIndex > 0) {
                RaftLog.LogEntry entry = log.getEntry(prevLogIndex);
                if (entry != null) prevLogTerm = entry.getTerm();
            }
            
            // Entries? For heartbeat empty list
            // For replication, we would fetch entries from log
            // Let's implement full replication later, for now Heartbeat (empty entries)
            List<RaftLog.LogEntry> entries = new ArrayList<>();
            
            long leaderCommit = 0; // TODO: Maintain commitIndex
            
            sendAppendEntriesAsync(url, currentTerm, nodeId, prevLogIndex, prevLogTerm, entries, leaderCommit)
                .thenAccept(success -> {
                    if (success) {
                        // update nextIndex/matchIndex
                        matchIndex.put(peerId, prevLogIndex + entries.size());
                        nextIndex.put(peerId, matchIndex.get(peerId) + 1);
                    } else {
                        // decrement nextIndex and retry (simple logic)
                        long next = nextIndex.getOrDefault(peerId, 1L);
                        if (next > 1) nextIndex.put(peerId, next - 1);
                    }
                });
        }
    }
    
    private java.util.concurrent.CompletableFuture<Boolean> sendAppendEntriesAsync(String url, long term, String leaderId, long prevLogIndex, long prevLogTerm, List<RaftLog.LogEntry> entries, long leaderCommit) {
        Map<String, Object> body = new java.util.HashMap<>();
        body.put("term", term);
        body.put("leaderId", leaderId);
        body.put("prevLogIndex", prevLogIndex);
        body.put("prevLogTerm", prevLogTerm);
        body.put("entries", entries.stream().map(RaftLog.LogEntry::toMap).collect(java.util.stream.Collectors.toList()));
        body.put("leaderCommit", leaderCommit);

        return sendRequest(url + "/raft/append", body)
             .thenApply(responseMap -> {
                 if (responseMap == null) return false;
                 long responseTerm = ((Number) responseMap.get("term")).longValue();
                 if (responseTerm > currentTerm) {
                     currentTerm = responseTerm;
                     state = State.FOLLOWER;
                     votedFor = null;
                 }
                 return (boolean) responseMap.get("success");
            })
            .exceptionally(e -> false);
    }

    private java.util.concurrent.CompletableFuture<Map<String, Object>> sendRequest(String url, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .timeout(java.time.Duration.ofMillis(500))
                    .build();

            return httpClient.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> {
                        if (res.statusCode() == 200) {
                            try {
                                return mapper.readValue(res.body(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
                            } catch (Exception e) {
                                e.printStackTrace();
                                return null;
                            }
                        }
                        return null;
                    });
        } catch (Exception e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }

    public void addPeer(String id, String url) {
        peerUrls.put(id, url);
        persistPeer(id, url);
    }
    
    private void persistPeer(String id, String url) {
        try {
            // Check if exists? uniqueness?
            Map<String, Object> doc = new java.util.HashMap<>();
            doc.put("type", "peer");
            doc.put("nodeId", id);
            doc.put("url", url);
            // We need a unique ID for the doc. Let's use "peer_" + id
            doc.put("_id", "peer_" + id); 
            // Save to _system/_cluster
            store.save(SYSTEM_DB, CLUSTER_COL, doc);
        } catch (Exception e) {
            LOGGER.warning("Failed to persist peer " + id);
            e.printStackTrace();
        }
    }
    
    public void removePeer(String id) {
        peerUrls.remove(id);
        deletePeer(id);
    }
    
    private void deletePeer(String id) {
        try {
            store.delete(SYSTEM_DB, CLUSTER_COL, "peer_" + id);
        } catch (Exception e) {
            LOGGER.warning("Failed to delete peer " + id);
        }
    }
    
    public Map<String, String> getPeers() {
        return Collections.unmodifiableMap(peerUrls);
    }
    
    public State getState() { return state; }
    public String getLeaderId() { return state == State.LEADER ? nodeId : votedFor; /* Approx */ } 
    public boolean isLeader() { return state == State.LEADER; }
    public String getNodeId() { return nodeId; }
    public long getCurrentTerm() { return currentTerm; }
    
    public static class RequestVoteResponse {
        public long term;
        public boolean voteGranted;
        public RequestVoteResponse(long term, boolean voteGranted) { this.term = term; this.voteGranted = voteGranted; }
    }
    
    public static class AppendEntriesResponse {
        public long term;
        public boolean success;
        public AppendEntriesResponse(long term, boolean success) { this.term = term; this.success = success; }
    }
}
