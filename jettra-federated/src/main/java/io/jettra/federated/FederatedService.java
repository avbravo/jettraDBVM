package io.jettra.federated;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

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
                users = mapper.readValue(usersFile, Map.class);
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
        rules.post("/raft/appendEntries", this::handleAppendEntries);
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
            Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            Map<String, Object> result = raftNode.handleAppendEntries(data);
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
                status.put("raftPeers", raftNode.getPeers());
            }
            res.send(mapper.writeValueAsString(status));
        } catch (Exception e) {
            res.status(500).send(e.getMessage());
        }
    }

    private void register(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> data = mapper.readValue(req.content().as(byte[].class), Map.class);
            String id = (String) data.get("nodeId");
            String url = (String) data.get("url");
            engine.registerNode(id, url);
            res.send("Registered");
        } catch (Exception e) {
            res.status(400).send(e.getMessage());
        }
    }

    private void heartbeat(ServerRequest req, ServerResponse res) {
        try {
            String nodeId = req.query().get("nodeId");
            engine.heartbeat(nodeId);
            res.send("OK");
        } catch (Exception e) {
            res.status(400).send(e.getMessage());
        }
    }
}
