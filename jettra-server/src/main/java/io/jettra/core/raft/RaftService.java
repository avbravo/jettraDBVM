package io.jettra.core.raft;

import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;

public class RaftService implements HttpService {
    private static final Logger LOGGER = Logger.getLogger(RaftService.class.getName());
    private final RaftNode raftNode;
    private final ObjectMapper mapper = new ObjectMapper();

    public RaftService(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    @Override
    public void routing(HttpRules rules) {
        rules.post("/requestvote", this::handleRequestVote);

        rules.post("/appendentries", this::handleAppendEntries);
        // rules.post("/installconfig", this::handleInstallConfig); // Removed
        rules.post("/installsnapshot", this::handleInstallSnapshot);
        rules.get("/status", this::handleStatus);
        rules.post("/stop", this::handleStop);
    }

    private void handleRequestVote(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> body = mapper.readValue(req.content().inputStream(), Map.class);
            int term = (int) body.get("term");
            String candidateId = (String) body.get("candidateId");
            int lastLogIndex = (int) body.getOrDefault("lastLogIndex", 0);
            int lastLogTerm = (int) body.getOrDefault("lastLogTerm", 0);

            boolean voteGranted = raftNode.requestVote(term, candidateId, lastLogIndex, lastLogTerm);

            res.send(mapper.writeValueAsString(Map.of("term", raftNode.getCurrentTerm(), "voteGranted", voteGranted)));
        } catch (Exception e) {
            res.status(500).send("Error processing RequestVote: " + e.getMessage());
        }
    }

    private void handleAppendEntries(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> body = mapper.readValue(req.content().inputStream(), Map.class);
            int term = (int) body.get("term");
            String leaderId = (String) body.get("leaderId");
            int prevLogIndex = (int) body.getOrDefault("prevLogIndex", 0);
            int prevLogTerm = (int) body.getOrDefault("prevLogTerm", 0);
            int leaderCommit = (int) body.getOrDefault("leaderCommit", 0);

            @SuppressWarnings("unchecked")
            Map<String, Object> command = (Map<String, Object>) body.get("command");

            boolean success;
            if (command != null) {
                success = raftNode.appendEntry(term, leaderId, command);
            } else {
                success = raftNode.appendEntries(term, leaderId, prevLogIndex, prevLogTerm, null, leaderCommit);
            }

            res.send(mapper.writeValueAsString(Map.of("term", raftNode.getCurrentTerm(), "success", success)));
        } catch (Exception e) {
            res.status(500).send("Error processing AppendEntries: " + e.getMessage());
        }
    }

    private void handleInstallSnapshot(ServerRequest req, ServerResponse res) {
        try {
            java.io.InputStream is = req.content().inputStream();
            raftNode.installSnapshot(is);
            res.send(mapper.writeValueAsString(Map.of("success", true)));
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to install snapshot", e);
            res.status(500).send("Error processing InstallSnapshot: " + e.getMessage());
        }
    }

    private void handleStatus(ServerRequest req, ServerResponse res) {
        try {
            res.send(mapper.writeValueAsString(raftNode.getClusterStatus()));
        } catch (Exception e) {
            res.status(500).send("Error getting status: " + e.getMessage());
        }
    }

    private void handleStop(ServerRequest req, ServerResponse res) {
        res.send("Stopping...");
        LOGGER.info("Received remote stop command. Shutting down...");
        System.exit(0);
    }
}
