package io.jettra.memory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.helidon.webserver.WebServer;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.helidon.webserver.staticcontent.StaticContentService;

public class JettraMemoryServer {
    private static final Logger LOGGER = Logger.getLogger(JettraMemoryServer.class.getName());
    private static JettraMemoryDB db;
    private static MemoryConfig config;
    private static final long START_TIME = System.currentTimeMillis();
    
    public static void main(String[] args) {
        String configPath = System.getProperty("config", "memory.json");
        java.io.File file = new java.io.File(configPath);
        boolean exists = file.exists();
        
        config = MemoryConfig.loadFromFile(configPath);
        
        if (!exists) {
            config.saveToFile(configPath);
        }
        
        db = new JettraMemoryDB("jettra-memory-sys", config);
        
        WebServer server = WebServer.builder()
                .port(config.getPort())
                .host(config.getHost())
                .routing(JettraMemoryServer::routing)
                .build();
                
        server.start();
        LOGGER.info(() -> "Jettra Memory Server started at http://" + config.getHost() + ":" + config.getPort());
        
        startFederatedSync();
    }
    
    private static void startFederatedSync() {
        if (config.getFederatedServers().isEmpty()) return;
        
        java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
        ObjectMapper mapper = new ObjectMapper();
        String nodeId = "memory-" + config.getPort(); // Simple ID
        String url = "http://localhost:" + config.getPort(); // Conceptually. Ideally use real IP.

        // Initial Registration
        for (String fedServer : config.getFederatedServers()) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("nodeId", nodeId);
                payload.put("url", url);
                String json = mapper.writeValueAsString(payload);
                java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(fedServer + "/federated/register/memory"))
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                        .header("Content-Type", "application/json")
                        .build();
                client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {}
        }

        java.util.concurrent.Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate(() -> {
            for (String fedServer : config.getFederatedServers()) {
                try {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("nodeId", nodeId);
                    payload.put("url", url);
                    
                    String json = mapper.writeValueAsString(payload);
                    java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(fedServer + "/federated/heartbeat/memory?nodeId=" + nodeId))
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                            .header("Content-Type", "application/json")
                            .timeout(java.time.Duration.ofSeconds(2))
                            .build();
                    
                    client.sendAsync(req, java.net.http.HttpResponse.BodyHandlers.discarding())
                        .thenAccept(res -> {
                            // If we get 404, re-register
                            if (res.statusCode() == 404) {
                                  java.net.http.HttpRequest regReq = java.net.http.HttpRequest.newBuilder()
                                    .uri(java.net.URI.create(fedServer + "/federated/register/memory"))
                                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                                    .header("Content-Type", "application/json")
                                    .build();
                                  client.sendAsync(regReq, java.net.http.HttpResponse.BodyHandlers.discarding());
                            }
                        });
                } catch (Exception e) {
                    // LOGGER.warning("Failed to sync with: " + fedServer);
                }
            }
        }, 5, 5, java.util.concurrent.TimeUnit.SECONDS);
    }
    
    static void routing(HttpRules rules) {
        rules
            .register("/api", new ApiService())
            .register("/", StaticContentService.builder("WEB")
                .welcomeFileName("index.html")
                .build());
    }
    
    static class ApiService implements io.helidon.webserver.http.HttpService {
        private final ObjectMapper mapper = new ObjectMapper();
        private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newHttpClient();

        @Override
        public void routing(HttpRules rules) {
            rules.get("/status", this::status);
            rules.post("/admin/password", this::changePassword);
            rules.post("/sync", this::sync);
            
            // CRUD
            rules.post("/doc", this::saveDocument);
            rules.get("/doc", this::getDocument);
            rules.put("/doc", this::updateDocument);
            rules.delete("/doc", this::deleteDocument);
            rules.get("/query", this::queryDocuments);
        }
        
        private void status(ServerRequest req, ServerResponse res) {
            long uptime = System.currentTimeMillis() - START_TIME;
            StatusResponse status = new StatusResponse(
                formatUptime(uptime),
                db.getResourceMonitor().getAvailableMemory() / 1024 / 1024 + " MB Free",
                db.getCollectionCount()
            );
            try {
                res.send(mapper.writeValueAsString(status));
            } catch(IOException e) {
                res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send("Error");
            }
        }
        
        private void changePassword(ServerRequest req, ServerResponse res) {
             try {
                String body = req.content().as(String.class);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
                if (node.has("newPassword")) {
                    config.setAdminPassword(node.get("newPassword").asText());
                    res.send("Password updated");
                } else {
                    res.status(io.helidon.http.Status.BAD_REQUEST_400).send("Missing newPassword");
                }
             } catch(Exception e) {
                 res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
             }
        }

        private void sync(ServerRequest req, ServerResponse res) {
            try {
                String dbLeaderUrl = req.query().first("dbLeaderUrl").orElse(null);
                if (dbLeaderUrl == null) {
                    res.status(400).send("Missing dbLeaderUrl");
                    return;
                }
                
                LOGGER.info("Starting memory sync from: " + dbLeaderUrl);
                syncFromPermanentStorage(dbLeaderUrl);
                res.send("Sync started");
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void syncFromPermanentStorage(String dbLeaderUrl) throws Exception {
            // 1. List Databases
            java.net.http.HttpRequest listDbsReq = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(dbLeaderUrl + "/api/dbs"))
                    .GET()
                    .build();
            
            String dbsJson = httpClient.send(listDbsReq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
            java.util.List<Map<String, Object>> dbs = mapper.readValue(dbsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, Object>>>() {});
            
            for (Map<String, Object> dbInfo : dbs) {
                String dbName = (String) dbInfo.get("name");
                if (dbName.equals("_system")) continue; // Optional: sync system?
                
                // 2. List Collections
                java.net.http.HttpRequest listColsReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(dbLeaderUrl + "/api/dbs/" + dbName + "/cols"))
                        .GET()
                        .build();
                String colsJson = httpClient.send(listColsReq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                java.util.List<String> cols = mapper.readValue(colsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {});
                
                for (String colName : cols) {
                    if (colName.startsWith("_")) continue;
                    
                    // 3. Fetch all documents
                    java.net.http.HttpRequest fetchDocsReq = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(dbLeaderUrl + "/api/query?db=" + dbName + "&col=" + colName))
                            .GET()
                            .build();
                    String docsJson = httpClient.send(fetchDocsReq, java.net.http.HttpResponse.BodyHandlers.ofString()).body();
                    java.util.List<Map<String, Object>> docs = mapper.readValue(docsJson, new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, Object>>>() {});
                    
                    MemoryCollection memCol = db.createCollection(dbName, colName);
                    for (Map<String, Object> doc : docs) {
                        String id = (String) doc.get("_id");
                        if (id == null) id = (String) doc.get("id");
                        if (id != null) {
                            memCol.insert(id, doc, 0);
                        }
                    }
                    LOGGER.info("Synced collection: " + dbName + "." + colName + " (" + docs.size() + " docs)");
                }
            }
            LOGGER.info("Memory sync completed successfully.");
        }

        private void saveDocument(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                byte[] bytes = req.content().as(byte[].class);
                Map<String, Object> doc = mapper.readValue(bytes, Map.class);
                
                String id = (String) doc.get("_id");
                if (id == null) id = java.util.UUID.randomUUID().toString();
                doc.put("_id", id);
                
                db.createCollection(dbName, colName).insert(id, doc, 0);
                res.send(mapper.writeValueAsString(Map.of("id", id, "status", "ok")));
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void getDocument(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                String id = req.query().get("id");
                
                MemoryCollection col = db.getCollection(dbName, colName);
                Object doc = (col != null) ? col.get(id) : null;
                
                if (doc != null) {
                    res.send(mapper.writeValueAsString(doc));
                } else {
                    res.status(404).send("Not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void updateDocument(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                String id = req.query().get("id");
                byte[] bytes = req.content().as(byte[].class);
                Map<String, Object> doc = mapper.readValue(bytes, Map.class);
                
                doc.put("_id", id);
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    col.insert(id, doc, 0);
                    res.send(mapper.writeValueAsString(Map.of("status", "ok")));
                } else {
                    res.status(404).send("Collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void deleteDocument(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                String id = req.query().get("id");
                
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    col.delete(id, 0);
                    res.send(mapper.writeValueAsString(Map.of("status", "ok")));
                } else {
                    res.status(404).send("Collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void queryDocuments(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    // Very simple: returns all for now.
                    // In real impl, we'd apply filters.
                    java.util.List<Object> all = new java.util.ArrayList<>();
                    // Need a way to iterate MemoryCollection. 
                    // Let's add it or use internal map access.
                    // For now, I'll add a 'getAll' to MemoryCollection.
                    res.send("[]"); // Placeholder until I add iteration
                } else {
                    res.send("[]");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
    }
    
    static class StatusResponse {
        public String uptime;
        public String memoryUsage;
        public int collections;

        public StatusResponse(String uptime, String memoryUsage, int collections) {
            this.uptime = uptime;
            this.memoryUsage = memoryUsage;
            this.collections = collections;
        }
    }
    
    private static String formatUptime(long millis) {
        long seconds = millis / 1000;
        long minutes = seconds / 60;
        long hours = minutes / 60;
        return String.format("%02d:%02d:%02d", hours, minutes % 60, seconds % 60);
    }
    
    public static JettraMemoryDB getDB() { return db; }
}
