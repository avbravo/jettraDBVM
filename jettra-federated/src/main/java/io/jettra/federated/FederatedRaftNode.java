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
    private final List<String> federatedPeers;
    private final FederatedEngine engine;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper();
    
    private long lastHeartbeatReceived;
    private final Random random = new Random();

    public FederatedRaftNode(String selfId, List<String> federatedPeers, FederatedEngine engine) {
        this.selfId = selfId;
        this.federatedPeers = federatedPeers;
        this.engine = engine;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    public void start() {
        lastHeartbeatReceived = System.currentTimeMillis();
        scheduler.scheduleAtFixedRate(this::tick, 500, 200, TimeUnit.MILLISECONDS);
        LOGGER.info("Federated Raft Node " + selfId + " started. Peers: " + federatedPeers);
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (state == State.LEADER) {
            sendHeartbeats();
        } else {
            // Check election timeout
            long timeout = 3000 + random.nextInt(2000); 
            if (now - lastHeartbeatReceived > timeout) {
                LOGGER.warning("Election timeout! Starting election.");
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
                .filter(url -> !url.contains("localhost:" + engine.getPort()))
                .filter(url -> !url.contains("127.0.0.1:" + engine.getPort()))
                .filter(url -> !url.contains("0.0.0.0:" + engine.getPort()))
                .toList();

        if (otherPeers.isEmpty()) {
            becomeLeader();
            return;
        }

        final java.util.concurrent.atomic.AtomicInteger votes = new java.util.concurrent.atomic.AtomicInteger(1);
        int majority = (federatedPeers.size() / 2) + 1;

        for (String peerUrl : otherPeers) {
             sendRequestVote(peerUrl, votes, majority);
        }
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

    private void sendRequestVote(String peerUrl, java.util.concurrent.atomic.AtomicInteger votes, int majority) {
         Map<String, Object> body = new HashMap<>();
         body.put("term", currentTerm);
         body.put("candidateId", selfId);
         
         sendAsync(peerUrl + "/federated/raft/vote", body).thenAccept(res -> {
             if (res != null) {
                 int term = (int) res.get("term");
                 boolean voteGranted = (boolean) res.get("voteGranted");
                 
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
         
         for (String peerUrl : federatedPeers) {
             if (peerUrl.contains("localhost:" + engine.getPort())) continue;
             sendAsync(peerUrl + "/federated/raft/appendEntries", body).thenAccept(res -> {
                 if (res != null) {
                     int term = (int) res.getOrDefault("term", 0);
                     if (term > currentTerm) {
                         stepDown(term);
                     }
                 }
             });
         }
    }

    // --- RPC Handlers ---

    public synchronized Map<String, Object> handleRequestVote(Map<String, Object> data) {
        int term = (int) data.get("term");
        String candidateId = (String) data.get("candidateId");
        
        Map<String, Object> response = new HashMap<>();
        response.put("term", currentTerm);
        response.put("voteGranted", false);

        if (term < currentTerm) {
            return response;
        }

        if (term > currentTerm) {
            stepDown(term);
        }

        if ((votedFor == null || votedFor.equals(candidateId))) {
            votedFor = candidateId;
            response.put("voteGranted", true);
            lastHeartbeatReceived = System.currentTimeMillis();
        }
        
        return response;
    }

    public synchronized Map<String, Object> handleAppendEntries(Map<String, Object> data) {
        int term = (int) data.get("term");
        String leaderId = (String) data.get("leaderId");
        
        Map<String, Object> response = new HashMap<>();
        response.put("term", currentTerm);
        response.put("success", false);

        if (term < currentTerm) {
            return response;
        }

        if (term > currentTerm) {
            stepDown(term);
        } else if (state != State.FOLLOWER) {
             stepDown(term);
        }

        this.leaderId = leaderId;
        this.lastHeartbeatReceived = System.currentTimeMillis();
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

    public HttpClient getHttpClient() {
        return httpClient;
    }
}
