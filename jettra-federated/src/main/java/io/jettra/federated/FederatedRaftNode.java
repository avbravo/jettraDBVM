package io.jettra.federated;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

public class FederatedRaftNode {
    private static final Logger LOGGER = Logger.getLogger(FederatedRaftNode.class.getName());

    public enum State { FOLLOWER, CANDIDATE, LEADER }

    private volatile State state = State.FOLLOWER;
    private volatile int currentTerm = 0;
    private volatile String votedFor = null;
    private volatile String leaderId = null;
    
    private final String selfId;
    private final String selfUrl;
    private final List<String> federatedPeers;
    private final Map<String, String> peerUrlToId = new HashMap<>();
    private final Map<String, String> peerStates = new HashMap<>();
    private final Map<String, Long> peerLastSeen = new HashMap<>();
    private final FederatedEngine engine;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    private final boolean bootstrap;
    
    private long lastHeartbeatReceived;
    private final Random random = new Random();
    private int unreachableCount = 0;
    private final java.util.function.Consumer<List<String>> configSaver;

    public FederatedRaftNode(String selfId, String selfUrl, List<String> federatedPeers, FederatedEngine engine, boolean bootstrap, java.util.function.Consumer<List<String>> configSaver) {
        this.selfId = selfId;
        this.selfUrl = selfUrl;
        this.federatedPeers = federatedPeers;
        this.engine = engine;
        this.bootstrap = bootstrap;
        this.configSaver = configSaver;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public void start() {
        lastHeartbeatReceived = System.currentTimeMillis();
        scheduler.scheduleAtFixedRate(this::tick, 500, 200, TimeUnit.MILLISECONDS);
        LOGGER.info("Federated Raft Node " + selfId + " started. Peers: " + federatedPeers);
        if (bootstrap) {
            LOGGER.info("Bootstrap enabled. Asserting leadership.");
            becomeLeader();
        } else {
            // New node or follower: try to join known peers
            joinCluster();
        }
    }

    public String getLeaderUrl() {
        if (state == State.LEADER) return selfUrl;
        if (leaderId == null) return null;
        for (Map.Entry<String, String> entry : peerUrlToId.entrySet()) {
            if (entry.getValue().equals(leaderId)) return entry.getKey();
        }
        return null;
    }

    public void joinCluster() {
        if (federatedPeers.size() <= 1 && !bootstrap) {
             LOGGER.info("No peers to join. Waiting for someone to find us or for election.");
             return;
        }
        
        for (String peerUrl : federatedPeers) {
            if (peerUrl.equals(selfUrl)) continue;
            
            Map<String, Object> body = new HashMap<>();
            body.put("url", selfUrl);
            body.put("nodeId", selfId);
            
            LOGGER.info("Attempting to join cluster via " + peerUrl);
            sendAsync(peerUrl + "/federated/raft/join", body).thenAccept(res -> {
                if (res != null) {
                    if (res.get("redirect") != null) {
                        String leaderUrl = (String) res.get("redirect");
                        LOGGER.info("Redirected join to leader: " + leaderUrl);
                        sendAsync(leaderUrl + "/federated/raft/join", body);
                    } else if (Boolean.TRUE.equals(res.get("success"))) {
                        LOGGER.info("Successfully joined cluster via " + peerUrl);
                    }
                }
            });
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (state == State.LEADER) {
            sendHeartbeats();
        } else {
            // Check election timeout
            long timeout = 3000 + random.nextInt(2000); 
            if (now - lastHeartbeatReceived > timeout) {
                LOGGER.fine("Election timeout! Starting election."); // Changed to fine to reduce noise
                startElection();
            }
        }
    }

    private void startElection() {
        state = State.CANDIDATE;
        currentTerm++;
        votedFor = selfId;
        lastHeartbeatReceived = System.currentTimeMillis();
        
        List<String> otherPeers = federatedPeers.stream()
                .filter(url -> !url.equals(selfUrl))
                .filter(url -> !url.contains("localhost:" + engine.getPort()))
                .filter(url -> !url.contains("127.0.0.1:" + engine.getPort()))
                .filter(url -> !url.contains("0.0.0.0:" + engine.getPort()))
                .toList();

        if (otherPeers.isEmpty()) {
            becomeLeader();
            return;
        }

        final java.util.concurrent.atomic.AtomicInteger votes = new java.util.concurrent.atomic.AtomicInteger(1);
        final java.util.concurrent.atomic.AtomicInteger responses = new java.util.concurrent.atomic.AtomicInteger(0);
        int majority = (federatedPeers.size() / 2) + 1;

        for (String peerUrl : otherPeers) {
             sendRequestVote(peerUrl, votes, majority, responses);
        }
        
        // Safety check for isolation
        scheduler.schedule(() -> {
            if (state == State.CANDIDATE && responses.get() == 0) {
                unreachableCount++;
                if (unreachableCount >= 3) {
                    LOGGER.warning("Multiple election cycles without any peer response. Assuming SOLITARY LEADERSHIP.");
                    becomeLeader();
                    unreachableCount = 0;
                }
            } else if (responses.get() > 0) {
                unreachableCount = 0;
            }
        }, 1500, TimeUnit.MILLISECONDS);
    }

    private void becomeLeader() {
        if (state != State.LEADER) {
            state = State.LEADER;
            leaderId = selfId;
            LOGGER.info("Became LEADER for term " + currentTerm);
            engine.onBecomeLeader();
            sendHeartbeats(); // Immediate heartbeat
        }
    }

    private void stepDown(int term) {
        if (state == State.LEADER) {
            engine.onStepDown();
        }
        state = State.FOLLOWER;
        currentTerm = term;
        votedFor = null;
        leaderId = null;
        lastHeartbeatReceived = System.currentTimeMillis();
    }

    // --- RPC Senders ---

    private void sendRequestVote(String peerUrl, java.util.concurrent.atomic.AtomicInteger votes, int majority, java.util.concurrent.atomic.AtomicInteger responses) {
         Map<String, Object> body = new HashMap<>();
         body.put("term", currentTerm);
         body.put("candidateId", selfId);
         body.put("url", selfUrl);
         
         sendAsync(peerUrl + "/federated/raft/vote", body).thenAccept(res -> {
             if (res != null) {
                 responses.incrementAndGet();
                 int term = (int) res.get("term");
                 boolean voteGranted = (boolean) res.get("voteGranted");
                 String responderId = (String) res.get("responderId");
                 String responderState = (String) res.get("responderState");
                 
                 if (responderId != null) {
                     peerUrlToId.put(peerUrl, responderId);
                 }
                 if (responderState != null) {
                     peerStates.put(peerUrl, responderState);
                 }
                 peerLastSeen.put(peerUrl, System.currentTimeMillis());
                 
                 if (term > currentTerm) {
                     stepDown(term);
                 } else if (state == State.CANDIDATE && voteGranted) {
                     if (votes.incrementAndGet() >= majority) {
                         becomeLeader();
                     }
                 }
             }
         });
    }

    private void sendHeartbeats() {
         Map<String, Object> body = new HashMap<>();
         body.put("term", currentTerm);
         body.put("leaderId", selfId);
         body.put("url", selfUrl);
         body.put("clusterState", engine.getClusterStatus());
         body.put("raftPeerStates", getPeerStates());
         body.put("raftPeerLastSeen", getPeerLastSeen());
         body.put("raftPeerIds", getPeerUrlToId());
         body.put("allPeers", federatedPeers);
         
         for (String peerUrl : federatedPeers) {
             if (peerUrl.equals(selfUrl)) continue;
             sendAsync(peerUrl + "/federated/raft/appendEntries", body).thenAccept(res -> {
                 if (res != null) {
                     int term = (int) res.getOrDefault("term", 0);
                     String responderId = (String) res.get("responderId");
                     String responderState = (String) res.get("responderState");
                     
                     if (responderId != null) {
                         peerUrlToId.put(peerUrl, responderId);
                     }
                     if (responderState != null) {
                         peerStates.put(peerUrl, responderState);
                     }
                     peerLastSeen.put(peerUrl, System.currentTimeMillis());

                     if (term > currentTerm) {
                         stepDown(term);
                     }
                 } else {
                     peerStates.put(peerUrl, "INACTIVE");
                 }
             });
         }
    }

    // --- RPC Handlers ---

    public synchronized Map<String, Object> handleRequestVote(Map<String, Object> data) {
        int term = (int) data.get("term");
        String candidateId = (String) data.get("candidateId");
        String candidateUrl = (String) data.get("url");
        if (candidateUrl != null) {
            peerUrlToId.put(candidateUrl, candidateId);
            peerStates.put(candidateUrl, "CANDIDATE");
            peerLastSeen.put(candidateUrl, System.currentTimeMillis());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("term", currentTerm);
        response.put("voteGranted", false);
        response.put("responderId", selfId);
        response.put("responderState", state.name());

        // Security / Integrity check: Do not vote for unknown peers unless we are in bootstrap/empty mode
        if (candidateUrl != null && !federatedPeers.isEmpty() && !federatedPeers.contains(candidateUrl)) {
            LOGGER.warning("Rejected vote from unknown peer: " + candidateUrl);
            return response;
        }

        if (term < currentTerm) {
            return response;
        }

        if (term > currentTerm) {
            stepDown(term);
            response.put("term", currentTerm);
            response.put("responderState", state.name()); // Reflect state after stepDown
        }

        if ((votedFor == null || votedFor.equals(candidateId))) {
            votedFor = candidateId;
            response.put("voteGranted", true);
            lastHeartbeatReceived = System.currentTimeMillis();
        }
        
        return response;
    }

    public synchronized Map<String, Object> handleJoin(Map<String, Object> data) {
        String peerUrl = (String) data.get("url");
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("responderId", selfId);

        if (state == State.LEADER) {
            if (peerUrl != null) {
                addPeer(peerUrl);
                response.put("success", true);
                response.put("leaderId", selfId);
                response.put("allPeers", federatedPeers);
            }
        } else {
            String lUrl = getLeaderUrl();
            if (lUrl != null) {
                response.put("redirect", lUrl);
            } else {
                // We don't know the leader, but we can add it as a peer to help election
                if (peerUrl != null) addPeer(peerUrl);
                response.put("success", true);
            }
        }
        return response;
    }

    public synchronized Map<String, Object> handleAppendEntries(Map<String, Object> data) {
        int term = (int) data.get("term");
        String leaderId = (String) data.get("leaderId");
        String leaderUrl = (String) data.get("url");
        if (leaderUrl != null) {
            peerUrlToId.put(leaderUrl, leaderId);
            peerStates.put(leaderUrl, "LEADER");
            peerLastSeen.put(leaderUrl, System.currentTimeMillis());
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("term", currentTerm);
        response.put("success", false);
        response.put("responderId", selfId);
        response.put("responderState", state.name());

        if (term < currentTerm) {
            return response;
        }

        if (term > currentTerm) {
            stepDown(term);
            response.put("term", currentTerm);
            response.put("responderState", state.name());
        } else if (state != State.FOLLOWER) {
             stepDown(term);
             response.put("responderState", state.name());
        }

        this.leaderId = leaderId;
        this.lastHeartbeatReceived = System.currentTimeMillis();
        if (data.containsKey("clusterState")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> clusterState = (Map<String, Object>) data.get("clusterState");
            engine.applyClusterState(clusterState);
        }

        // Sync peers from leader
        if (data.containsKey("allPeers")) {
            @SuppressWarnings("unchecked")
            List<String> leadersPeers = (List<String>) data.get("allPeers");
            if (leadersPeers != null && !leadersPeers.equals(federatedPeers)) {
                LOGGER.info("Updating peers list from leader: " + leadersPeers);
                federatedPeers.clear();
                federatedPeers.addAll(leadersPeers);
                if (configSaver != null) {
                    configSaver.accept(federatedPeers);
                }
            }
        }

        if (data.containsKey("raftPeerIds")) {
            @SuppressWarnings("unchecked")
            Map<String, String> pIds = (Map<String, String>) data.get("raftPeerIds");
            pIds.forEach((url, id) -> {
                if (!url.equals(selfUrl)) peerUrlToId.put(url, id);
            });
        }

        if (data.containsKey("raftPeerStates")) {
            @SuppressWarnings("unchecked")
            Map<String, String> pStates = (Map<String, String>) data.get("raftPeerStates");
            pStates.forEach((url, s) -> {
                if (!url.equals(selfUrl)) peerStates.put(url, s);
            });
        }
        
        if (data.containsKey("raftPeerLastSeen")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> pSeen = (Map<String, Object>) data.get("raftPeerLastSeen");
            pSeen.forEach((url, ts) -> {
                if (!url.equals(selfUrl)) {
                    if (ts instanceof Number) {
                        peerLastSeen.put(url, ((Number) ts).longValue());
                    }
                }
            });
        }
        
        response.put("success", true);
        
        return response;
    }

    // --- Helper ---

    private java.util.concurrent.CompletableFuture<Map<String, Object>> sendAsync(String url, Map<String, Object> body) {
        try {
            String json = mapper.writeValueAsString(body);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            
            return httpClient.sendAsync(req, HttpResponse.BodyHandlers.ofString())
                    .thenApply(res -> {
                        if (res.statusCode() == 200) {
                            try {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> map = (Map<String, Object>) mapper.readValue(res.body(), Map.class);
                                return map;
                            } catch (Exception e) {
                                return null;
                            }
                        }
                        return null;
                    })
                   .exceptionally(e -> null);
        } catch (JsonProcessingException e) {
            return java.util.concurrent.CompletableFuture.failedFuture(e);
        }
    }
    
    public boolean isLeader() {
        return state == State.LEADER;
    }
    
    public List<String> getPeers() {
        return federatedPeers;
    }

    public State getState() {
        return state;
    }

    public int getCurrentTerm() {
        return currentTerm;
    }

    public String getLeaderId() {
        return leaderId;
    }

    public String getSelfId() {
        return selfId;
    }

    public String getSelfUrl() {
        return selfUrl;
    }

    public Map<String, String> getPeerUrlToId() {
        return new HashMap<>(peerUrlToId);
    }

    public Map<String, String> getPeerStates() {
        return new HashMap<>(peerStates);
    }

    public Map<String, Long> getPeerLastSeen() {
        return new HashMap<>(peerLastSeen);
    }

    public synchronized void removePeer(String peerUrl) {
        if (federatedPeers.remove(peerUrl)) {
            peerUrlToId.remove(peerUrl);
            peerStates.remove(peerUrl);
            peerLastSeen.remove(peerUrl);
            LOGGER.info("Peer removed: " + peerUrl + ". New peer list: " + federatedPeers);
            if (configSaver != null) {
                configSaver.accept(federatedPeers);
            }
        }
    }

    public synchronized void addPeer(String peerUrl) {
        if (!federatedPeers.contains(peerUrl)) {
            federatedPeers.add(peerUrl);
            LOGGER.info("Peer added: " + peerUrl + ". New peer list: " + federatedPeers);
            if (configSaver != null) {
                configSaver.accept(federatedPeers);
            }
        }
    }

    public HttpClient getHttpClient() {
        return httpClient;
    }
}
