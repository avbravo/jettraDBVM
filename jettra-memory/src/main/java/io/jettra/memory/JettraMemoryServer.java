package io.jettra.memory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import com.fasterxml.jackson.core.type.TypeReference;
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
        MemoryConfig config = MemoryConfig.loadFromFile(configPath);
        startServer(config);
    }

    public static void startServer(MemoryConfig memoryConfig) {
        config = memoryConfig;
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
        if (config.getFederatedServers().isEmpty())
            return;

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
            } catch (Exception e) {
            }
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
        private final MemoryQueryExecutor queryExecutor = new MemoryQueryExecutor(db);

        @Override
        public void routing(HttpRules rules) {
            rules.get("/status", this::status);
            rules.post("/admin/password", this::changePassword);
            rules.post("/login", this::login);
            rules.post("/change-password", this::changePassword);
            rules.post("/sync", this::sync);

            rules.get("/dbs", this::listDatabases);
            rules.post("/dbs", this::createDatabase);
            rules.delete("/dbs", this::deleteDatabase);
            rules.get("/dbs/{db}/cols", this::listCollections);
            rules.post("/cols", this::createCollection);
            rules.delete("/cols", this::deleteCollection);
            rules.get("/metrics", this::metrics);

            // CRUD
            rules.post("/doc", this::saveDocument);
            rules.get("/doc", this::getDocument);
            rules.put("/doc", this::updateDocument);
            rules.delete("/doc", this::deleteDocument);
            rules.get("/query", this::queryDocuments);
            rules.get("/count", this::countDocuments);
            rules.post("/command", this::executeCommand);
            
            // System and Management
            rules.get("/cluster", (req, res) -> res.send("{\"nodes\":[]}"));
            rules.get("/config", this::getConfig);
            rules.post("/config", this::saveConfig);
            rules.get("/users", this::listUsers);
            rules.post("/users", this::createUser);
            rules.delete("/users", this::deleteUser);
            rules.get("/federated", (req, res) -> res.send("{}"));
            rules.get("/backups", this::listBackups);
            rules.post("/backup", this::performBackup);
            rules.get("/index", this::getIndexes);
            rules.post("/index", this::createIndex);
            rules.delete("/index", this::deleteIndex);
            rules.get("/versions", this::getVersions);
            rules.get("/version", this::getVersionContent);
            rules.post("/restore-version", this::restoreVersion);

            // Import/Export
            rules.get("/export", this::exportCollection);
            rules.post("/import", this::importCollection);
 
            // Transactions
            rules.post("/tx/begin", this::beginTransaction);
            rules.post("/tx/commit", this::commitTransaction);
            rules.post("/tx/rollback", this::rollbackTransaction);

            rules.get("/versions_placeholder", (req, res) -> res.send("[]"));
        }

        private void status(ServerRequest req, ServerResponse res) {
            long uptime = System.currentTimeMillis() - START_TIME;
            StatusResponse status = new StatusResponse(
                    formatUptime(uptime),
                    db.getResourceMonitor().getAvailableMemory() / 1024 / 1024 + " MB Free",
                    db.getCollectionCount());
            try {
                res.send(mapper.writeValueAsString(status));
            } catch (IOException e) {
                res.status(io.helidon.http.Status.INTERNAL_SERVER_ERROR_500).send("Error");
            }
        }

        private void login(ServerRequest req, ServerResponse res) {
            try {
                byte[] content = req.content().as(byte[].class);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(content);
                String user = node.get("username").asText();
                String pass = node.get("password").asText();

                if ("admin".equals(user) && config.getAdminPassword().equals(pass)) {
                    String token = java.util.Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                    res.send(mapper.createObjectNode()
                            .put("token", "Basic " + token)
                            .put("role", "admin")
                            .toString());
                } else {
                    res.status(io.helidon.http.Status.UNAUTHORIZED_401).send("Invalid credentials");
                }
            } catch (Exception e) {
                res.status(401).send("Error");
            }
        }

        private void changePassword(ServerRequest req, ServerResponse res) {
            try {
                String body = req.content().as(String.class);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(body);
                String newPass = null;
                if (node.has("newPassword")) {
                    newPass = node.get("newPassword").asText();
                } else if (node.has("password")) {
                    newPass = node.get("password").asText();
                }

                if (newPass != null) {
                    config.setAdminPassword(newPass);
                    res.send(mapper.createObjectNode().put("status", "Password updated").toString());
                } else {
                    res.status(io.helidon.http.Status.BAD_REQUEST_400).send("Missing newPassword");
                }
            } catch (Exception e) {
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
            java.util.List<Map<String, Object>> dbs = mapper.readValue(dbsJson,
                    new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, Object>>>() {
                    });

            for (Map<String, Object> dbInfo : dbs) {
                String dbName = (String) dbInfo.get("name");
                if (dbName.equals("_system"))
                    continue; // Optional: sync system?

                // 2. List Collections
                java.net.http.HttpRequest listColsReq = java.net.http.HttpRequest.newBuilder()
                        .uri(java.net.URI.create(dbLeaderUrl + "/api/dbs/" + dbName + "/cols"))
                        .GET()
                        .build();
                String colsJson = httpClient.send(listColsReq, java.net.http.HttpResponse.BodyHandlers.ofString())
                        .body();
                java.util.List<String> cols = mapper.readValue(colsJson,
                        new com.fasterxml.jackson.core.type.TypeReference<java.util.List<String>>() {
                        });

                for (String colName : cols) {
                    if (colName.startsWith("_"))
                        continue;

                    // 3. Fetch all documents
                    java.net.http.HttpRequest fetchDocsReq = java.net.http.HttpRequest.newBuilder()
                            .uri(java.net.URI.create(dbLeaderUrl + "/api/query?db=" + dbName + "&col=" + colName))
                            .GET()
                            .build();
                    String docsJson = httpClient.send(fetchDocsReq, java.net.http.HttpResponse.BodyHandlers.ofString())
                            .body();
                    java.util.List<Map<String, Object>> docs = mapper.readValue(docsJson,
                            new com.fasterxml.jackson.core.type.TypeReference<java.util.List<Map<String, Object>>>() {
                            });

                    MemoryCollection memCol = db.createCollection(dbName, colName);
                    for (Map<String, Object> doc : docs) {
                        String id = (String) doc.get("_id");
                        if (id == null)
                            id = (String) doc.get("id");
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
                if (id == null)
                    id = java.util.UUID.randomUUID().toString();
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
                    res.send(mapper.writeValueAsString(col.getAll().values()));
                } else {
                    res.send("[]");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void countDocuments(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                MemoryCollection col = db.getCollection(dbName, colName);
                res.send("{\"count\":" + (col != null ? col.size() : 0) + "}");
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void listDatabases(ServerRequest req, ServerResponse res) {
            try {
                java.util.List<Map<String, String>> dbs = new java.util.ArrayList<>();
                for (String name : db.getDatabaseNames()) {
                    dbs.add(Map.of("name", name, "engine", "JettraMemoryDB"));
                }
                res.send(mapper.writeValueAsString(dbs));
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void listCollections(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.path().pathParameters().get("db");
                java.util.Set<String> cols = db.getCollectionNames(dbName);
                res.send(mapper.writeValueAsString(cols));
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void createDatabase(ServerRequest req, ServerResponse res) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(req.content().as(byte[].class));
                String name = node.get("name").asText();
                db.getDatabase(name);
                res.send(mapper.createObjectNode().put("status", "Database created").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void deleteDatabase(ServerRequest req, ServerResponse res) {
            try {
                String name = req.query().get("name");
                if (name == null) {
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(req.content().as(byte[].class));
                    name = node.get("name").asText();
                }
                db.deleteDatabase(name);
                res.send(mapper.createObjectNode().put("status", "Database deleted").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void createCollection(ServerRequest req, ServerResponse res) {
            try {
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(req.content().as(byte[].class));
                String dbName = node.get("database").asText();
                String colName = node.get("collection").asText();
                db.createCollection(dbName, colName);
                res.send(mapper.createObjectNode().put("status", "Collection created").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void deleteCollection(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                if (dbName == null || colName == null) {
                    com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(req.content().as(byte[].class));
                    dbName = node.get("database").asText();
                    colName = node.get("collection").asText();
                }
                db.deleteCollection(dbName, colName);
                res.send(mapper.createObjectNode().put("status", "Collection deleted").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void metrics(ServerRequest req, ServerResponse res) {
            status(req, res);
        }

        private void executeCommand(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String command = req.content().as(String.class);
                Object result = queryExecutor.execute(dbName, command);
                res.send(mapper.writeValueAsString(result));
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void getConfig(ServerRequest req, ServerResponse res) {
            try {
                String configPath = System.getProperty("config", "memory.json");
                java.io.File file = new java.io.File(configPath);
                if (file.exists()) {
                    res.send(java.nio.file.Files.readString(file.toPath()));
                } else {
                    res.send(mapper.writeValueAsString(config));
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void saveConfig(ServerRequest req, ServerResponse res) {
            try {
                String body = req.content().as(String.class);
                String configPath = System.getProperty("config", "memory.json");
                java.nio.file.Files.writeString(java.nio.file.Paths.get(configPath), body);
                res.send(mapper.createObjectNode().put("status", "Config saved").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void listUsers(ServerRequest req, ServerResponse res) {
            try {
                MemoryCollection users = db.getCollection("_system", "users");
                if (users != null) {
                    res.send(mapper.writeValueAsString(users.getAll().values()));
                } else {
                    res.send("[]");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void createUser(ServerRequest req, ServerResponse res) {
            try {
                byte[] bytes = req.content().as(byte[].class);
                Map<String, Object> user = mapper.readValue(bytes, Map.class);
                String username = (String) user.get("username");
                if (username == null) username = (String) user.get("_id");
                if (username == null) {
                    res.status(400).send("Missing username");
                    return;
                }
                user.put("_id", username);
                db.createCollection("_system", "users").insert(username, user, 0);
                res.send(mapper.createObjectNode().put("status", "User created").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void deleteUser(ServerRequest req, ServerResponse res) {
            try {
                String username = req.query().get("id");
                MemoryCollection users = db.getCollection("_system", "users");
                if (users != null) {
                    users.delete(username, 0);
                    res.send(mapper.createObjectNode().put("status", "User deleted").toString());
                } else {
                    res.status(404).send("Users collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void listBackups(ServerRequest req, ServerResponse res) {
            try {
                java.io.File backupDir = new java.io.File("backups");
                if (!backupDir.exists()) backupDir.mkdirs();
                java.io.File[] files = backupDir.listFiles((d, name) -> name.endsWith(".json"));
                java.util.List<Map<String, Object>> backups = new java.util.ArrayList<>();
                if (files != null) {
                    for (java.io.File f : files) {
                        backups.add(Map.of(
                                "filename", f.getName(),
                                "date", new java.util.Date(f.lastModified()).toString(),
                                "size", f.length()
                        ));
                    }
                }
                res.send(mapper.writeValueAsString(backups));
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void performBackup(ServerRequest req, ServerResponse res) {
            try {
                java.io.File backupDir = new java.io.File("backups");
                if (!backupDir.exists()) backupDir.mkdirs();
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss").format(new java.util.Date());
                java.io.File backupFile = new java.io.File(backupDir, "manual_backup_" + timestamp + ".json");
                
                // Simple state dump
                Map<String, Object> state = new HashMap<>();
                for (String dbName : db.getDatabaseNames()) {
                    Map<String, Object> colData = new HashMap<>();
                    for (String colName : db.getCollectionNames(dbName)) {
                        colData.put(colName, db.getCollection(dbName, colName).getAll());
                    }
                    state.put(dbName, colData);
                }
                mapper.writerWithDefaultPrettyPrinter().writeValue(backupFile, state);
                res.send(mapper.createObjectNode().put("status", "Backup successful").put("file", backupFile.getName()).toString());
            } catch (Exception e) {
                res.status(500).send("Backup failed: " + e.getMessage());
            }
        }
 
        private void exportCollection(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col == null) {
                    res.status(404).send("Collection not found");
                    return;
                }
                res.send(mapper.writeValueAsString(col.getAll().values()));
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }
 
        private void importCollection(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                byte[] bytes = req.content().as(byte[].class);
                java.util.List<Map<String, Object>> docs = mapper.readValue(bytes, new TypeReference<List<Map<String, Object>>>() {});
                
                MemoryCollection col = db.createCollection(dbName, colName);
                for (Map<String, Object> doc : docs) {
                    String id = (String) doc.get("_id");
                    if (id == null) id = (String) doc.get("id");
                    if (id == null) id = UUID.randomUUID().toString();
                    col.insert(id, doc, 0);
                }
                res.send(mapper.createObjectNode().put("status", "Import successful").put("count", docs.size()).toString());
            } catch (Exception e) {
                res.status(500).send("Import failed: " + e.getMessage());
            }
        }

        private void getIndexes(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    res.send(mapper.writeValueAsString(col.getIndexes()));
                } else {
                    res.send("[]");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void createIndex(ServerRequest req, ServerResponse res) {
            try {
                byte[] content = req.content().as(byte[].class);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(content);
                String dbName = node.get("database").asText();
                String colName = node.get("collection").asText();
                String field = node.get("field").asText();
                boolean unique = node.has("unique") && node.get("unique").asBoolean();
                boolean sequential = node.has("sequential") && node.get("sequential").asBoolean();

                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    Map<String, Object> index = new HashMap<>();
                    index.put("field", field);
                    index.put("unique", unique);
                    index.put("sequential", sequential);
                    col.createIndex(index);
                    res.send(mapper.createObjectNode().put("status", "Index created").toString());
                } else {
                    res.status(404).send("Collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void deleteIndex(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                String field = req.query().get("field");
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    col.deleteIndex(field);
                    res.send(mapper.createObjectNode().put("status", "Index deleted").toString());
                } else {
                    res.status(404).send("Collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void getVersions(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                String id = req.query().get("id");
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    java.util.List<String> versions = col.getVersions(id).stream()
                            .map(v -> v.versionId())
                            .collect(java.util.stream.Collectors.toList());
                    res.send(mapper.writeValueAsString(versions));
                } else {
                    res.send("[]");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void getVersionContent(ServerRequest req, ServerResponse res) {
            try {
                String dbName = req.query().get("db");
                String colName = req.query().get("col");
                String id = req.query().get("id");
                String version = req.query().get("version");
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    MemoryCollection.DocVersion v = col.getVersion(id, version);
                    if (v != null) {
                        res.send(mapper.writeValueAsString(v.data()));
                    } else {
                        res.status(404).send("Version not found");
                    }
                } else {
                    res.status(404).send("Collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void restoreVersion(ServerRequest req, ServerResponse res) {
            try {
                byte[] content = req.content().as(byte[].class);
                com.fasterxml.jackson.databind.JsonNode node = mapper.readTree(content);
                String dbName = node.get("db").asText();
                String colName = node.get("col").asText();
                String id = node.get("id").asText();
                String version = node.get("version").asText();
                MemoryCollection col = db.getCollection(dbName, colName);
                if (col != null) {
                    col.restoreVersion(id, version);
                    res.send(mapper.createObjectNode().put("status", "restored").toString());
                } else {
                    res.status(404).send("Collection not found");
                }
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void beginTransaction(ServerRequest req, ServerResponse res) {
            try {
                long txID = db.getTransactionManager().beginTransaction();
                res.send(mapper.createObjectNode().put("txID", String.valueOf(txID)).toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void commitTransaction(ServerRequest req, ServerResponse res) {
            try {
                String txID = req.query().get("txID");
                if (txID == null) {
                    res.status(400).send("Missing txID");
                    return;
                }
                db.getTransactionManager().commit(Long.parseLong(txID));
                res.send(mapper.createObjectNode().put("status", "committed").toString());
            } catch (Exception e) {
                res.status(500).send(e.getMessage());
            }
        }

        private void rollbackTransaction(ServerRequest req, ServerResponse res) {
            try {
                String txID = req.query().get("txID");
                if (txID == null) {
                    res.status(400).send("Missing txID");
                    return;
                }
                db.getTransactionManager().rollback(Long.parseLong(txID));
                res.send(mapper.createObjectNode().put("status", "rolled back").toString());
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

    public static JettraMemoryDB getDB() {
        return db;
    }
}
