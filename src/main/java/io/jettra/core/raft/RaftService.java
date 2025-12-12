package io.jettra.core.raft;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.util.Map;
import java.util.List;

public class RaftService {
    private final RaftNode raftNode;
    private final ObjectMapper mapper = new ObjectMapper();

    public RaftService(RaftNode raftNode) {
        this.raftNode = raftNode;
    }

    public void register(HttpRules rules) {
        rules
            .post("/raft/vote", this::requestVote)
            .post("/raft/append", this::appendEntries)
            // Management
            .get("/raft/status", this::getStatus)
            .post("/raft/peers", this::addPeer)
            .delete("/raft/peers", this::removePeer)
            .post("/raft/force-leader", this::forceLeader);
    }

    private void requestVote(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> body = mapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            
            long term = ((Number) body.get("term")).longValue();
            String candidateId = (String) body.get("candidateId");
            long lastLogIndex = ((Number) body.get("lastLogIndex")).longValue();
            long lastLogTerm = ((Number) body.get("lastLogTerm")).longValue();
            
            RaftNode.RequestVoteResponse response = raftNode.handleRequestVote(term, candidateId, lastLogIndex, lastLogTerm);
            res.send(mapper.writeValueAsString(response));
        } catch (Exception e) {
            res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void appendEntries(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> body = mapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            
            long term = ((Number) body.get("term")).longValue();
            String leaderId = (String) body.get("leaderId");
            long prevLogIndex = ((Number) body.get("prevLogIndex")).longValue();
            long prevLogTerm = ((Number) body.get("prevLogTerm")).longValue();
            long leaderCommit = ((Number) body.get("leaderCommit")).longValue();
            
            // Need to parse entries
            List<RaftLog.LogEntry> entries = new java.util.ArrayList<>();
            List<Map<String, Object>> entriesList = (List<Map<String, Object>>) body.get("entries");
            if (entriesList != null) {
                for (Map<String, Object> entryMap : entriesList) {
                    entries.add(RaftLog.LogEntry.fromMap(entryMap));
                }
            }
            
            RaftNode.AppendEntriesResponse response = raftNode.handleAppendEntries(term, leaderId, prevLogIndex, prevLogTerm, entries, leaderCommit);
            res.send(mapper.writeValueAsString(response));
        } catch (Exception e) {
            e.printStackTrace();
            res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }
    
    private void getStatus(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> status = new java.util.HashMap<>();
            status.put("nodeId", raftNode.getNodeId());
            status.put("state", raftNode.getState().toString());
            status.put("isLeader", raftNode.isLeader());
            status.put("peers", raftNode.getPeers());
            res.send(mapper.writeValueAsString(status));
        } catch (Exception e) {
             res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }
    
    private void addPeer(ServerRequest req, ServerResponse res) {
        try {
             byte[] content = req.content().as(byte[].class);
            Map<String, String> body = mapper.readValue(content, new TypeReference<Map<String, String>>() {});
            String id = body.get("id");
            String url = body.get("url");
            raftNode.addPeer(id, url);
            res.send("{\"status\": \"ok\"}");
        } catch(Exception e) {
            res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }
    
    private void removePeer(ServerRequest req, ServerResponse res) {
         try {
            String id = req.query().get("id");
            raftNode.removePeer(id);
            res.send("{\"status\": \"ok\"}");
        } catch(Exception e) {
            res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }
    
    private void forceLeader(ServerRequest req, ServerResponse res) {
        // Special manual override
        // TODO: Access private method via reflection or expose public method?
        // For now, expose nothing, just log
        res.send("{\"status\": \"Not implemented yet\"}");
    }
}
