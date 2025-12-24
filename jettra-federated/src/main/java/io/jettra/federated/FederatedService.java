package io.jettra.federated;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jettra.federated.util.HotReloadManager;

public class FederatedService implements HttpService {
    private final FederatedEngine engine;
    private final FederatedRaftNode raftNode;
    private final ObjectMapper mapper = new ObjectMapper();
    private final File usersFile = new File("federated_users.json");
    private Map<String, String> users = new HashMap<>();

    public FederatedService(FederatedEngine engine, FederatedRaftNode raftNode) {
        this.engine = engine;
        this.raftNode = raftNode;
        loadUsers();
    }

    private void loadUsers() {
        if (usersFile.exists()) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, String> loadedUsers = mapper.readValue(usersFile, Map.class);
                users = loadedUsers;
            } catch (IOException e) {
                System.err.println("Error loading users: " + e.getMessage());
            }
        }
        if (users.isEmpty()) {
            users.put("admin", "adminadmin");
            saveUsers();
        }
    }

    private void saveUsers() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(usersFile, users);
        } catch (IOException e) {
            System.err.println("Error saving users: " + e.getMessage());
        }
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/login", this::handleLogin);
        rules.post("/change-password", this::handleChangePassword);
        rules.get("/status", this::getStatus);
        rules.post("/register", this::register);
        rules.post("/heartbeat", this::heartbeat);
        rules.post("/raft/vote", this::handleVote);
        rules.post("/raft/join", this::handleJoin);
        rules.post("/raft/appendEntries", this::handleAppendEntries);
        rules.get("/config", this::getConfig);
        rules.post("/config", this::saveConfig);
        rules.get("/node-config/{nodeId}", this::getNodeConfig);
        rules.post("/node-config/{nodeId}", this::saveNodeConfig);
        rules.post("/node/stop/{nodeId}", this::handleStopNode);
        rules.post("/node/restart/{nodeId}", this::handleRestartNode);
        rules.post("/node/remove/{nodeId}", this::handleRemoveNode);
        rules.get("/node-leader", this::getNodeLeader);
        rules.post("/raft/removePeer", this::handleRemovePeer);
        rules.post("/raft/addPeer", this::handleAddPeer);
        rules.post("/restart", this::handleRestart);
        rules.post("/stop", (req, res) -> this.handleStopFederated(req, res));
    }

    private void handleLogin(ServerRequest req, ServerResponse res) {
        try {
            Map<String, String> creds = mapper.readValue(req.content().as(byte[].class), Map.class);
            String user = creds.get("username");
            String pass = creds.get("password");

            String storedPass = users.get(user);
            if (storedPass != null && storedPass.equals(pass)) {
                res.send(mapper.writeValueAsString(Map.of("token", "fed-secret-token-" + System.currentTimeMillis())));
            } else {
                res.status(401).send("Invalid credentials");
            }
        } catch (Exception e) {
            res.status(400).send(e.getMessage());
        }
    }

    private void handleChangePassword(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            String user = data.get("username");
            String oldPass = data.get("oldPassword");
            String newPass = data.get("newPassword");

            String storedPass = users.get(user);
            if (storedPass != null && storedPass.equals(oldPass)) {
                users.put(user, newPass);
                saveUsers();
                res.send(mapper.writeValueAsString(Map.of("status", "success")));
            } else {
                res.status(401).send("Invalid current password");
            }
        } catch (Exception e) {
            res.status(400).send(e.getMessage());
        }
    }

    private void handleVote(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            Map<String, Object> result = raftNode.handleRequestVote(data);
            res.send(mapper.writeValueAsString(result));
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void handleAppendEntries(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            Map<String, Object> result = raftNode.handleAppendEntries(data);
            res.send(mapper.writeValueAsString(result));
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void handleJoin(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            Map<String, Object> result = raftNode.handleJoin(data);
            res.send(mapper.writeValueAsString(result));
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void getStatus(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> status = engine.getClusterStatus();
            if (raftNode != null) {
                status.put("raftState", raftNode.getState());
                status.put("raftTerm", raftNode.getCurrentTerm());
                status.put("raftLeaderId", raftNode.getLeaderId());
                String leaderUrl = null;
                if (raftNode.getLeaderId() != null) {
                    if (raftNode.getLeaderId().equals(raftNode.getSelfId())) {
                        leaderUrl = raftNode.getSelfUrl();
                    } else {
                        // Find URL from peer maps
                        for (Map.Entry<String, String> entry : raftNode.getPeerUrlToId().entrySet()) {
                            if (entry.getValue().equals(raftNode.getLeaderId())) {
                                leaderUrl = entry.getKey();
                                break;
                            }
                        }
                    }
                }
                status.put("raftLeaderUrl", leaderUrl);
                status.put("raftSelfId", raftNode.getSelfId());
                status.put("raftSelfUrl", raftNode.getSelfUrl());
                status.put("raftPeers", raftNode.getPeers());
                status.put("raftPeerIds", raftNode.getPeerUrlToId());
                status.put("raftPeerStates", raftNode.getPeerStates());
                status.put("raftPeerLastSeen", raftNode.getPeerLastSeen());
            }
            res.send(mapper.writeValueAsString(status));
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void register(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            String id = (String) data.get("nodeId");
            String url = (String) data.get("url");
            @SuppressWarnings("unchecked")
            List<String> clientFedServers = (List<String>) data.get("FederatedServers");
            
            if (!raftNode.isLeader()) {
                String leaderUrl = raftNode.getLeaderUrl();
                if (leaderUrl != null) {
                    res.status(307).header("Location", leaderUrl + "/federated/register").send();
                    return;
                }
            }
            
            Map<String, Object> additionalInfo = new HashMap<>();
            if (data.containsKey("metrics")) {
                additionalInfo.put("metrics", data.get("metrics"));
            }
            if (data.containsKey("description")) {
                additionalInfo.put("description", data.get("description"));
            }
            
            engine.registerNode(id, url, additionalInfo);
            
            Map<String, Object> status = engine.getClusterStatus();
            if (clientFedServers != null && raftNode != null) {
                List<String> serverFedServers = raftNode.getPeers();
                if (!serverFedServers.equals(clientFedServers)) {
                    status.put("FederatedServers", serverFedServers);
                }
            }
            res.send(mapper.writeValueAsString(status));
        } catch (Exception e) {
            res.status(400).send(e.getMessage());
        }
    }

    private void heartbeat(ServerRequest req, ServerResponse res) {
        try {
            String nodeId = req.query().get("nodeId");
            if (!raftNode.isLeader()) {
                String leaderUrl = raftNode.getLeaderUrl();
                if (leaderUrl != null) {
                    res.status(307).header("Location", leaderUrl + "/federated/heartbeat?nodeId=" + nodeId).send();
                    return;
                }
            }
            
            Map<String, Object> additionalInfo = null;
            if (req.headers().contentLength().orElse(0L) > 0) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
                    additionalInfo = new HashMap<>();
                    if (data.containsKey("metrics")) {
                        additionalInfo.put("metrics", data.get("metrics"));
                    }
                    
                    // Body processing for FederatedServers sync already exists below, but we can reuse data here
                    @SuppressWarnings("unchecked")
                    List<String> clientFedServers = (List<String>) data.get("FederatedServers");
                    if (clientFedServers != null && raftNode != null) {
                        List<String> serverFedServers = raftNode.getPeers();
                        if (!serverFedServers.equals(clientFedServers)) {
                            // We will add this to the status returned later
                        }
                    }
                } catch (Exception e) {
                    // Ignore body errors
                }
            }
            
            engine.heartbeat(nodeId, additionalInfo);
            
            Map<String, Object> status = engine.getClusterStatus();
            
            // Check if client sent FederatedServers in body for synchronization
            if (req.headers().contentLength().orElse(0L) > 0) {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
                    @SuppressWarnings("unchecked")
                    List<String> clientFedServers = (List<String>) data.get("FederatedServers");
                    if (clientFedServers != null && raftNode != null) {
                        List<String> serverFedServers = raftNode.getPeers();
                        if (!serverFedServers.equals(clientFedServers)) {
                            status.put("FederatedServers", serverFedServers);
                        }
                    }
                } catch (Exception e) {
                    // Ignore body errors in heartbeat
                }
            }
            
            res.send(mapper.writeValueAsString(status));
        } catch (Exception e) {
            res.status(400).send(e.getMessage());
        }
    }

    private void getConfig(ServerRequest req, ServerResponse res) {
        try {
            File configFile = new File("config.json");
            if (!configFile.exists()) {
                configFile = new File("federated.json");
            }
            if (configFile.exists()) {
                Map<String, Object> config = mapper.readValue(configFile, Map.class);
                res.send(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
            } else {
                res.send("{}");
            }
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void saveConfig(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> newConfig = mapper.readValue(content, Map.class);
            File configFile = new File("config.json");
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, newConfig);
            
            boolean restart = req.query().first("restart").map(Boolean::parseBoolean).orElse(false);
            if (restart) {
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        HotReloadManager.restart();
                    } catch (Exception e) {}
                }).start();
            }
            
            res.send(mapper.writeValueAsString(Map.of("status", "success")));
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void getNodeConfig(ServerRequest req, ServerResponse res) {
        String nodeId = req.path().pathParameters().get("nodeId");
        @SuppressWarnings("unchecked")
        Map<String, Object> node = engine.getDbNodes().get(nodeId);
        String url = (node != null) ? (String) node.get("url") : null;

        if (url == null) {
            res.status(404).send("Node not found");
            return;
        }

        try {
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url + "/api/config"))
                    .GET()
                    .timeout(java.time.Duration.ofMillis(3000))
                    .build();

            raftNode.getHttpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        res.send(response.body());
                    })
                    .exceptionally(ex -> {
                        res.status(500).send("Node unreachable: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void saveNodeConfig(ServerRequest req, ServerResponse res) {
        String nodeId = req.path().pathParameters().get("nodeId");
        @SuppressWarnings("unchecked")
        Map<String, Object> node = engine.getDbNodes().get(nodeId);
        String url = (node != null) ? (String) node.get("url") : null;

        if (url == null) {
            res.status(404).send("Node not found");
            return;
        }

        try {
            byte[] content = req.content().as(byte[].class);
            boolean restart = req.query().first("restart").map(Boolean::parseBoolean).orElse(false);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(url + "/api/config"))
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofByteArray(content))
                    .header("Content-Type", "application/json")
                    .timeout(java.time.Duration.ofMillis(3000))
                    .build();

            raftNode.getHttpClient().sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenCompose(response -> {
                        if (restart && response.statusCode() == 200) {
                            java.net.http.HttpRequest restartReq = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(url + "/api/cluster/restart"))
                                    .POST(java.net.http.HttpRequest.BodyPublishers.noBody())
                                    .timeout(java.time.Duration.ofMillis(3000))
                                    .build();
                            return raftNode.getHttpClient().sendAsync(restartReq, java.net.http.HttpResponse.BodyHandlers.ofString());
                        }
                        return java.util.concurrent.CompletableFuture.completedFuture(response);
                    })
                    .thenAccept(response -> {
                        res.send(response.body());
                    })
                    .exceptionally(ex -> {
                        res.status(500).send("Node unreachable: " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }
    private void handleStopNode(ServerRequest req, ServerResponse res) {
        String nodeId = req.path().pathParameters().get("nodeId");
        engine.stopNode(nodeId);
        res.send(Map.of("status", "stop_sent").toString());
    }

    private void handleRestartNode(ServerRequest req, ServerResponse res) {
        String nodeId = req.path().pathParameters().get("nodeId");
        engine.restartNode(nodeId);
        res.send(Map.of("status", "restart_sent").toString());
    }

    private void handleRemoveNode(ServerRequest req, ServerResponse res) {
        String nodeId = req.path().pathParameters().get("nodeId");
        engine.removeNode(nodeId);
        res.send(Map.of("status", "removed").toString());
    }

    private void handleRestart(ServerRequest req, ServerResponse res) {
        try {
            res.send(mapper.writeValueAsString(Map.of("status", "restarting")));
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    HotReloadManager.restart();
                } catch (Exception e) {
                }
            }).start();
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void handleStopFederated(ServerRequest req, ServerResponse res) {
        // Authenticate - for now let's just do it if token is provided or simple check
        // Ideally we check if it's the right token.
        
        try {
            res.send(mapper.writeValueAsString(Map.of("status", "stopping")));
        } catch (Exception e) {
            res.send("{\"status\":\"stopping\"}");
        }
        
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                System.exit(0);
            } catch (Exception e) {}
        }).start();
    }


    private void getNodeLeader(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> status = engine.getClusterStatus();
            String leaderId = (String) status.get("leaderId");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> nodes = (List<Map<String, Object>>) status.get("nodes");

            Map<String, Object> leaderInfo = null;
            if (leaderId != null && nodes != null) {
                for (Map<String, Object> node : nodes) {
                    if (leaderId.equals(node.get("id"))) {
                        leaderInfo = node;
                        break;
                    }
                }
            }

            if (leaderInfo != null) {
                res.send(mapper.writeValueAsString(leaderInfo));
            } else {
                res.status(404).send(mapper.writeValueAsString(Map.of("error", "No active DB leader found")));
            }
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void handleRemovePeer(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            String url = data.get("url");
            if (url != null) {
                raftNode.removePeer(url);
                res.send(mapper.writeValueAsString(Map.of("status", "success", "peerRemoved", url)));
            } else {
                res.status(400).send("Missing url");
            }
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }


    private void handleAddPeer(ServerRequest req, ServerResponse res) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, String> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            String url = data.get("url");
            if (url != null) {
                if (!url.startsWith("http")) url = "http://" + url;
                
                if (raftNode.isLeader()) {
                    raftNode.addPeer(url);
                    res.send(mapper.writeValueAsString(Map.of("status", "success", "peerAdded", url)));
                } else {
                    String leaderUrl = raftNode.getLeaderUrl();
                    if (leaderUrl != null) {
                        res.status(307).header("Location", leaderUrl + "/federated/raft/addPeer").send();
                    } else {
                        // If no leader known, just add locally to help cluster form
                        raftNode.addPeer(url);
                        res.send(mapper.writeValueAsString(Map.of("status", "success", "peerAddedLocally", url)));
                    }
                }
            } else {
                res.status(400).send("Missing url");
            }
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }
}
