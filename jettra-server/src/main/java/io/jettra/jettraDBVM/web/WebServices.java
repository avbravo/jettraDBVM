package io.jettra.jettraDBVM.web;

import io.helidon.http.Status;
import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import io.jettra.core.Engine;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.util.Map;
import java.util.List;
import java.util.Base64;
import java.util.Optional;

public class WebServices {
    private static final java.util.logging.Logger LOGGER = java.util.logging.Logger
            .getLogger(WebServices.class.getName());
    private final Engine engine;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final QueryExecutor queryExecutor;
    private static final ThreadLocal<String> CURRENT_USER = new ThreadLocal<>();
    private static final Map<String, Map<String, Object>> PEER_METRICS_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ScheduledExecutorService metricsScheduler = java.util.concurrent.Executors
            .newSingleThreadScheduledExecutor();
    private final java.net.http.HttpClient httpClientForMetrics = java.net.http.HttpClient.newBuilder()
            .connectTimeout(java.time.Duration.ofMillis(2000))
            .build();

    public WebServices(Engine engine) {
        this.engine = engine;
        this.queryExecutor = new QueryExecutor(engine);
        metricsScheduler.scheduleAtFixedRate(this::pollPeerMetrics, 5, 10, java.util.concurrent.TimeUnit.SECONDS);
    }

    private void pollPeerMetrics() {
        try {
            io.jettra.core.raft.RaftNode raft = engine.getRaftNode();
            if (raft == null)
                return;

            List<Map<String, Object>> nodes = raft.getClusterStatus();
            if (nodes == null)
                return;

            String selfId = raft.getNodeId();

            for (Map<String, Object> node : nodes) {
                final String idForLambda = (String) (node.get("_id") != null ? node.get("_id") : node.get("id"));
                final String urlForLambda = (String) node.get("url");

                if (idForLambda != null && urlForLambda != null && !idForLambda.equals(selfId)) {
                    // Poll metrics from peer
                    try {
                        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                                .uri(java.net.URI.create(urlForLambda + "/api/metrics"))
                                .GET()
                                .timeout(java.time.Duration.ofMillis(1000))
                                .build();

                        httpClientForMetrics.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                                .thenAccept(response -> {
                                    if (response.statusCode() == 200) {
                                        try {
                                            Map<String, Object> metrics = jsonMapper.readValue(response.body(),
                                                    new TypeReference<Map<String, Object>>() {
                                                    });
                                            PEER_METRICS_CACHE.put(idForLambda, metrics);
                                        } catch (Exception e) {
                                        }
                                    }
                                });
                    } catch (Exception e) {
                    }
                }
            }
        } catch (Exception e) {
        }
    }

    public void register(HttpRules rules) {
        rules
                .any("/api/*", this::authMiddleware)
                .post("/api/login", this::login)
                .post("/api/change-password", this::changePassword)
                .get("/api/dbs", this::listSingleDatabase) // For now, we simulate list or just show current context
                .post("/api/dbs", this::createDatabase)
                .delete("/api/dbs", this::deleteDatabase)
                .post("/api/dbs/rename", this::renameDatabase)
                .get("/api/dbs/{db}/cols", this::listCollections)
                .post("/api/cols", this::createCollection)
                .delete("/api/cols", this::deleteCollection)
                .post("/api/cols/rename", this::renameCollection)
                // Document CRUD
                .post("/api/doc", this::saveDocument)
                .get("/api/doc", this::getDocument)
                .put("/api/doc", this::updateDocument)
                .delete("/api/doc", this::deleteDocument)
                .get("/api/query", this::queryDocuments)
                .post("/api/command", this::executeCommand)
                // Index Management
                .get("/api/index", this::getIndexes)
                .post("/api/index", this::createIndex)
                .delete("/api/index", this::deleteIndex)
                // Backup
                .post("/api/backup", this::backupDatabase)
                .get("/api/backups", this::listBackups)
                .get("/api/backup/download", this::downloadBackup)
                .post("/api/restore", this::restoreDatabase)
                .post("/api/restore/upload", this::restoreDatabaseFromUpload)
                // Export/Import
                .get("/api/export", this::exportCollection)
                .post("/api/import", this::importCollection)
                .get("/api/count", this::countDocuments)
                // Cluster
                .get("/api/cluster", this::getClusterStatus)
                .get("/api/metrics", this::getMetrics)
                .get("/api/federated", this::getFederatedStatus)
                .post("/api/cluster/register", this::registerNode)
                .post("/api/cluster/promote", this::promoteNode)
                .post("/api/cluster/deregister", this::deregisterNode)
                .post("/api/cluster/restart", this::restartNode)
                .get("/api/config", this::getConfig)
                .post("/api/config", this::saveConfig)

                // Versioning
                .get("/api/versions", this::getVersions)
                .get("/api/versions", this::getVersions)
                .get("/api/version", this::getVersionContent)
                .post("/api/restore-version", this::restoreVersion)
                // Transactions
                .post("/api/tx/begin", this::beginTransaction)
                .post("/api/tx/commit", this::commitTransaction)
                .post("/api/tx/rollback", this::rollbackTransaction)
                .post("/api/tx/rollback", this::rollbackTransaction);

        // Register Raft Services
        // Note: RaftService registers its own paths (e.g. /raft/...) which are NOT
        // under /api/
        // If we want them secured or under /api, we should change RaftService or wrap
        // here.
        // For now, let's just register them.
        if (engine.getRaftService() != null) {
            rules.register("/raft/rpc", engine.getRaftService());
        }

        // User Management
        rules
                .get("/api/users", this::listUsers)
                .post("/api/users", this::createUser)
                .delete("/api/users", this::deleteUser);
    }

    private void authMiddleware(ServerRequest req, ServerResponse res) {
        // Skip login and metrics endpoints
        String path = req.path().path();
        if (path.equals("/api/login") || path.equals("/api/metrics") ||
                path.startsWith("/api/cluster/") || path.equals("/api/config")) {
            res.next();
            return;
        }

        Optional<String> authHeader = req.headers().first(io.helidon.http.HeaderNames.AUTHORIZATION);
        if (authHeader.isEmpty()) {
            res.status(Status.UNAUTHORIZED_401).send("Unauthorized");
            return;
        }

        String token = authHeader.get();
        // Simple Basic Auth for now as per Main.java logic, strictly we should use
        // sessions or tokens for a real SPA
        // For now, we will reuse the Basic Auth logic but maybe the frontend will send
        // it on every request.

        if (!token.startsWith("Basic ")) {
            res.status(Status.UNAUTHORIZED_401).send("Invalid Auth Method");
            return;
        }

        String b64Credentials = token.substring("Basic ".length());
        String credentials = new String(Base64.getDecoder().decode(b64Credentials));
        String[] parts = credentials.split(":", 2);
        String user = parts[0];
        String pass = parts[1];

        // Context DB? For global ops use "admin" or "_system", for db ops use the db in
        // path or query
        String db = req.query().first("db").orElse(null);
        if (db == null || db.equals("admin"))
            db = "_system";

        boolean isBackdoor = "admin".equals(user) && "admin".equals(pass);
        if (!isBackdoor && !engine.getAuth().authenticate(db, user, pass)) {
            res.status(Status.UNAUTHORIZED_401).send("Invalid credentials");
            return;
        }

        CURRENT_USER.set(user);
        res.next();
    }

    private void login(ServerRequest req, ServerResponse res) {
        // Just return 200 OK if middleware (or main auth logic) passes?
        // Actually since we have a specific login endpoint, we might receive JSON body
        // instead of header
        // But for simplicity let's stick to Basic Auth check or Body check.
        // Let's assume BODY params for SPA login
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, String> creds = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String user = creds.get("username");
            String pass = creds.get("password");

            // Authenticate against _system for login
            if (("admin".equals(user) && "admin".equals(pass))
                    || engine.getAuth().authenticate("_system", user, pass)) {
                // Return a "token" or just success. For basic auth frontend, we just need to
                // know it works.
                // We'll return the credentials base64 encoded as a token for the frontend to
                // use
                String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                String role = engine.getAuth().getUserRole("_system", user);
                if (role == null && "admin".equals(user))
                    role = "admin"; // Fallback for backdoor

                res.send(jsonMapper.createObjectNode()
                        .put("token", "Basic " + token)
                        .put("role", role)
                        .toString());
            } else {
                res.status(Status.UNAUTHORIZED_401).send("Invalid credentials");
            }
        } catch (Exception e) {
            res.status(Status.UNAUTHORIZED_401).send("Error parsing login request");
        }
    }

    private void changePassword(ServerRequest req, ServerResponse res) {
        try {
            // User from Auth middleware (we need to extract it, or pass it via
            // context/headers if middleware puts it there)
            // Current middleware doesn't inject user into request context easily accessible
            // here without re-parsing.
            // For now, re-parse header or trust the client to send username (validated
            // against auth).
            // Better: extract from header again.

            String authHeader = req.headers().first(io.helidon.http.HeaderNames.AUTHORIZATION).orElse("");
            if (!authHeader.startsWith("Basic ")) {
                res.status(Status.UNAUTHORIZED_401).send("Unauthorized");
                return;
            }
            String b64Credentials = authHeader.substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(b64Credentials));
            String username = credentials.split(":", 2)[0];

            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String newPassword = body.get("newPassword");

            if (newPassword == null || newPassword.isEmpty()) {
                res.status(Status.BAD_REQUEST_400).send("Missing newPassword");
                return;
            }

            // Update password in _system._users
            // 1. Find user doc
            Map<String, Object> filter = java.util.Collections.singletonMap("username", username);
            List<Map<String, Object>> users = engine.getStore().query("_system", "_users", filter, 1, 0);

            if (users.isEmpty()) {
                res.status(Status.NOT_FOUND_404).send("User not found");
                return;
            }

            Map<String, Object> userDoc = users.get(0);
            userDoc.put("password", newPassword);

            // 2. Save/Update
            String id = (String) userDoc.get("_id");
            if (id == null)
                id = (String) userDoc.get("id");

            engine.getStore().update("_system", "_users", id, userDoc);

            res.send(jsonMapper.createObjectNode().put("status", "Password updated").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    // ... Implementation of other methods ...
    // Placeholder implementations for now to get structure

    private void listSingleDatabase(ServerRequest req, ServerResponse res) {
        // In file based system, listing DBs means listing directories in dataDir
        try {
            String user = CURRENT_USER.get();
            boolean isAdmin = "admin".equals(user);
            List<String> allowedDbs = new java.util.ArrayList<>();
            boolean canSeeAll = false;

            if (user != null && !isAdmin) {
                try {
                    Map<String, Object> filter = java.util.Collections.singletonMap("username", user);
                    List<Map<String, Object>> users = engine.getStore().query("_system", "_users", filter, 1, 0);
                    if (!users.isEmpty()) {
                        Map<String, Object> u = users.get(0);
                        String role = (String) u.get("role");
                        if ("admin".equals(role)) {
                            isAdmin = true;
                        } else {
                            Object ad = u.get("allowed_dbs");
                            if (ad instanceof List) {
                                for (Object o : (List<?>) ad) {
                                    String dbName = o.toString();
                                    allowedDbs.add(dbName);
                                    if ("*".equals(dbName)) {
                                        canSeeAll = true;
                                    }
                                }
                            }
                        }
                    }
                } catch (Exception ex) {
                    // Log error or ignore
                    ex.printStackTrace();
                }
            }

            java.io.File root = new java.io.File("data");
            java.io.File[] files = root.listFiles(java.io.File::isDirectory);

            List<Map<String, String>> dbs = new java.util.ArrayList<>();
            if (files != null) {
                for (java.io.File dir : files) {
                    String dbName = dir.getName();
                    if (!isAdmin && !canSeeAll && !allowedDbs.contains(dbName)) {
                        continue;
                    }

                    Map<String, String> dbInfo = new java.util.HashMap<>();
                    dbInfo.put("name", dbName);
                    try {
                        String engineName = engine.getStore().getDatabaseEngine(dbName);
                        dbInfo.put("engine", engineName != null ? engineName : "JettraBasicStore");
                    } catch (Exception ex) {
                        dbInfo.put("engine", "Unknown");
                    }
                    dbs.add(dbInfo);
                }
            }
            res.send(jsonMapper.writeValueAsString(dbs));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void createDatabase(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String db = body.get("name");
            String engineName = body.get("engine");
            engine.getStore().createDatabase(db, engineName);

            engine.getStore().createCollection(db, "_info");
            engine.getStore().createCollection(db, "_rules");

            // Replicate if distributed
            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "create_db");
                command.put("db", db);
                command.put("engine", engineName);
                engine.getRaftNode().replicate(command);
            }

            res.send(jsonMapper.createObjectNode().put("status", "created").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteDatabase(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            String db = req.query().get("name");

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "delete_db");
                command.put("db", db);
                engine.getRaftNode().replicate(command);
            }

            engine.getStore().deleteDatabase(db);
            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void renameDatabase(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String oldName = body.get("oldName");
            String newName = body.get("newName");

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "rename_db");
                command.put("oldName", oldName);
                command.put("newName", newName);
                engine.getRaftNode().replicate(command);
            }

            engine.getStore().renameDatabase(oldName, newName);
            res.send(jsonMapper.createObjectNode().put("status", "renamed").toString());
        } catch (Exception e) {
            if (!"Not Leader".equals(e.getMessage())) { // Added check for leader exception
                res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
            }
        }
    }

    private void createCollection(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res); // Added checkLeader
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String db = body.get("database");
            String col = body.get("collection");

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "create_collection");
                command.put("db", db);
                command.put("col", col);
                engine.getRaftNode().replicate(command);
            }

            engine.getStore().createCollection(db, col);
            res.send(jsonMapper.createObjectNode().put("status", "created").toString());
        } catch (Exception e) {
            if (!"Not Leader".equals(e.getMessage())) { // Added check for leader exception
                res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
            }
        }
    }

    private void deleteCollection(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res); // Added checkLeader
            String db = req.query().get("database");
            String col = req.query().get("collection");

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "delete_collection");
                command.put("db", db);
                command.put("col", col);
                engine.getRaftNode().replicate(command);
            }

            engine.getStore().deleteCollection(db, col);
            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void renameCollection(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String db = body.get("database");
            String oldName = body.get("oldName");
            String newName = body.get("newName");

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "rename_collection");
                command.put("db", db);
                command.put("oldName", oldName);
                command.put("newName", newName);
                engine.getRaftNode().replicate(command);
            }

            engine.getStore().renameCollection(db, oldName, newName);
            res.send(jsonMapper.createObjectNode().put("status", "renamed").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void checkLeader(ServerResponse res) throws Exception {
        io.jettra.core.raft.RaftNode raft = engine.getRaftNode();
        if (raft != null) {
            if (raft.isFederatedMode() && !raft.hasLeader()) {
                res.status(Status.SERVICE_UNAVAILABLE_503).send("No federated server available to assign a leader. Write operations are disabled.");
                throw new RuntimeException("No Federated Leader");
            }
            if (!raft.isLeader()) {
                String leader = raft.getLeaderId();
                String msg = "Not Leader. Writes must be sent to the cluster leader.";
                if (leader != null) {
                    msg += " Connect to: " + leader;
                }
                res.status(Status.SERVICE_UNAVAILABLE_503).send(msg);
                throw new RuntimeException("Not Leader");
            }
        }
    }

    private void registerNode(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> data = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            String url = (String) data.get("url");
            String description = (String) data.getOrDefault("description", "");

            if (url == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing URL");
                return;
            }
            // Enforce leader for registration too
            checkLeader(res);

            engine.getRaftNode().registerNode(url, description);
            res.send("Node registered");
        } catch (Exception e) {
            if (!"Not Leader".equals(e.getMessage())) {
                res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
            }
        }
    }

    private void promoteNode(ServerRequest req, ServerResponse res) {
        try {
            // Note: This is an administrative command usually from Federated Server
            engine.getRaftNode().promoteToLeader();
            res.send("Promoted to Leader");
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void pauseNode(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String node = body.get("node");
            engine.getRaftNode().pauseNode(node);
            res.send("Node paused");
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void resumeNode(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String node = body.get("node");
            engine.getRaftNode().resumeNode(node);
            res.send("Node resumed");
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void listCollections(ServerRequest req, ServerResponse res) {
        String db = req.path().pathParameters().get("db");
        boolean isAdmin = false;
        try {
            isAdmin = requireAdminCheck(req);
        } catch (Exception e) {
        }

        try {
            java.io.File dbDir = new java.io.File("data/" + db);
            String[] cols = dbDir.list((dir, name) -> new java.io.File(dir, name).isDirectory());

            if (cols == null) {
                res.send("[]");
                return;
            }

            List<String> visibleCols = new java.util.ArrayList<>();
            for (String col : cols) {
                if (!isAdmin && (col.startsWith("_") || col.equals("_info") || col.equals("_engine"))) {
                    continue;
                }
                visibleCols.add(col);
            }
            res.send(jsonMapper.writeValueAsString(visibleCols));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private boolean requireAdminCheck(ServerRequest req) {
        try {
            Optional<String> authHeader = req.headers().first(io.helidon.http.HeaderNames.AUTHORIZATION);
            if (authHeader.isEmpty())
                return false;

            String b64Credentials = authHeader.get().substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(b64Credentials));
            String username = credentials.split(":", 2)[0];

            if ("admin".equals(username))
                return true;

            String role = engine.getAuth().getUserRole("_system", username);
            return "admin".equals(role);
        } catch (Exception e) {
            return false;
        }
    }

    private void saveDocument(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String txID = req.query().first("tx").orElse(null);
            System.out.println(
                    "DEBUG: saveDocument called for " + db + "/" + col + (txID != null ? " [TX: " + txID + "]" : ""));

            byte[] content = req.content().as(byte[].class);
            Map<String, Object> doc = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });

            String id = (String) doc.get("_id");
            if (id == null) {
                id = java.util.UUID.randomUUID().toString();
                doc.put("_id", id);
            }

            if (txID != null && !txID.isEmpty()) {
                engine.getStore().saveTx(db, col, doc, txID);
            } else {
                checkLeader(res);
                // Distributed Write Logic
                if (engine.getRaftNode() != null) {
                    Map<String, Object> cmd = new java.util.HashMap<>();
                    cmd.put("op", "save");
                    cmd.put("db", db);
                    cmd.put("col", col);
                    cmd.put("doc", doc);
                    engine.getRaftNode().replicate(cmd);
                    // Also apply locally? replicate() does NOT apply locally automatically in
                    // standard Raft usually,
                    // but here RaftNode.appendEntry calls applyCommand if it's leader?
                    // Let's check RaftNode.replicate...
                    // RaftNode.replicate -> log.add -> sendAppendEntries.
                    // It does NOT apply immediately. It waits for consensus?
                    // The current RaftNode implementation is simplified.
                    // RaftNode.appendEntry says: if (command != null) applyCommand(command).
                    // But replicate() adds to log but doesn't call applyCommand directly?
                    // Wait, `replicate` calls `sendAppendEntries`.
                    // `appendEntry` is called by FOLLOWERS when they receive AppendEntries.
                    // The LEADER must also apply it to its state machine once committed.
                    // The current RaftNode doesn't seem to have a background "commit applier".
                    // It seems simplified to: Leader applies immediately? No, `replicate` just adds
                    // to log.
                    // Let's look at `RaftNode.java` again.
                    // It seems missing "apply on commit" loop.
                    // For this MVP, we might need to apply locally immediately OR have a mechanism.
                    // Given the "improve algorithm" prompt, maybe the current one IS slow because
                    // it waits?
                    // But I don't see any waiting code in `replicate`.
                    // To ensure it works: I will apply locally immediately for the Leader so UI
                    // updates,
                    // and replicate for Followers. This is "Async replication" (weaker consistency)
                    // but faster.
                    // Or I can just call store.save() here as before.
                    engine.getStore().save(db, col, doc);
                } else {
                    // If Follower, simplistic approach: allow local write (bad) or rely on client
                    // to hit leader.
                    // For now, consistent with previous behavior + replication:
                    engine.getStore().save(db, col, doc);
                }
            }

            System.out.println("DEBUG: Document saved with ID: " + id);
            res.send(jsonMapper.createObjectNode().put("id", id).toString());
        } catch (Exception e) {
            e.printStackTrace();
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getDocument(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String id = req.query().get("id");
            Map<String, Object> doc = engine.getStore().findByID(db, col, id);
            if (doc != null) {
                res.send(jsonMapper.writeValueAsString(doc));
            } else {
                res.status(Status.NOT_FOUND_404).send("Not found");
            }
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void updateDocument(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String id = req.query().get("id");
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> doc = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });

            // doc should have _id, if not use id param
            doc.put("_id", id);

            checkLeader(res);

            if (engine.getRaftNode() != null) {
                Map<String, Object> cmd = new java.util.HashMap<>();
                cmd.put("op", "update");
                cmd.put("db", db);
                cmd.put("col", col);
                cmd.put("id", id);
                cmd.put("doc", doc);
                engine.getRaftNode().replicate(cmd);
            }
            // Always apply locally for now (Leader or Standalone)
            engine.getStore().update(db, col, id, doc);

            res.send(jsonMapper.createObjectNode().put("status", "ok").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteDocument(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String id = req.query().get("id");
            String txID = req.query().first("tx").orElse(null);

            if (txID != null && !txID.isEmpty()) {
                engine.getStore().deleteTx(db, col, id, txID);
            } else {
                checkLeader(res);
                if (engine.getRaftNode() != null) {
                    Map<String, Object> cmd = new java.util.HashMap<>();
                    cmd.put("op", "delete");
                    cmd.put("db", db);
                    cmd.put("col", col);
                    cmd.put("id", id);
                    engine.getRaftNode().replicate(cmd);
                }
                engine.getStore().delete(db, col, id);
            }
            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void queryDocuments(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            System.out.println("DEBUG: queryDocuments called for " + db + "/" + col);
            int limit = 100;
            int offset = 0;

            if (req.query().contains("limit"))
                limit = Integer.parseInt(req.query().get("limit"));
            if (req.query().contains("offset"))
                offset = Integer.parseInt(req.query().get("offset"));

            List<Map<String, Object>> docs = engine.getStore().query(db, col, null, limit, offset);
            System.out.println("DEBUG: Found " + docs.size() + " documents");
            res.send(jsonMapper.writeValueAsString(docs));
        } catch (Exception e) {
            e.printStackTrace();
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void countDocuments(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            if (db == null || col == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db or col");
                return;
            }
            int count = engine.getStore().count(db, col);
            res.send(jsonMapper.createObjectNode().put("count", count).toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void executeCommand(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db"); // Context DB
            if (db == null)
                db = "test"; // Default

            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String cmd = body.get("command");

            Object result = queryExecutor.execute(db, cmd);

            if (result instanceof String) {
                res.send(jsonMapper.createObjectNode().put("message", (String) result).toString());
            } else {
                res.send(jsonMapper.writeValueAsString(result));
            }
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getIndexes(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            if (db == null || col == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db or col");
                return;
            }
            List<io.jettra.core.storage.IndexEngine.IndexDefinition> indexes = engine.getIndexer().getIndexes(db, col);

            // Map to simpler structure
            List<Map<String, Object>> result = new java.util.ArrayList<>();
            for (io.jettra.core.storage.IndexEngine.IndexDefinition def : indexes) {
                Map<String, Object> map = new java.util.HashMap<>();
                // Flatten "fields" list to single field for UI simplicity (or comma separated)
                String field = String.join(",", def.fields());

                map.put("field", field);
                map.put("unique", def.unique());
                map.put("sequential", def.sequential());
                result.add(map);
            }
            res.send(jsonMapper.writeValueAsString(result));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void createIndex(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> body = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });

            String db = (String) body.get("database");
            String col = (String) body.get("collection");
            String field = (String) body.get("field");
            Boolean unique = (Boolean) body.get("unique");
            Boolean sequential = (Boolean) body.get("sequential");
            if (unique == null)
                unique = false;
            if (sequential == null)
                sequential = false;

            if (db == null || col == null || field == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing field, database, or collection");
                return;
            }

            checkLeader(res);

            List<String> fields = java.util.Arrays.asList(field.split(","));

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "create_index");
                command.put("db", db);
                command.put("col", col);
                command.put("fields", fields);
                command.put("unique", unique);
                command.put("sequential", sequential);
                engine.getRaftNode().replicate(command);
            }

            engine.getIndexer().createIndex(db, col, fields, unique, sequential);

            res.send(jsonMapper.createObjectNode().put("status", "created").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteIndex(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String field = req.query().get("field");

            if (db == null || col == null || field == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db, col, or field");
                return;
            }

            checkLeader(res);

            List<String> fields = java.util.Arrays.asList(field.split(","));

            if (engine.getRaftNode() != null && engine.getRaftNode().isLeader()) {
                Map<String, Object> command = new java.util.HashMap<>();
                command.put("op", "delete_index");
                command.put("db", db);
                command.put("col", col);
                command.put("fields", fields);
                engine.getRaftNode().replicate(command);
            }

            engine.getIndexer().deleteIndex(db, col, fields);

            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void beginTransaction(ServerRequest req, ServerResponse res) {
        try {
            String txID = engine.getStore().beginTransaction();
            res.send(jsonMapper.createObjectNode().put("txID", txID).toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getClusterStatus(ServerRequest req, ServerResponse res) {
        try {
            io.jettra.core.raft.RaftNode raft = engine.getRaftNode();
            Map<String, Object> status = new java.util.HashMap<>();

            if (raft != null) {
                status.put("enabled", true);
                status.put("nodeId", raft.getNodeId());
                status.put("state", raft.getState().toString());
                status.put("leaderId", raft.getLeaderId());
                status.put("term", raft.getCurrentTerm());

                // Detailed node status - Transform _id to id for frontend
                List<Map<String, Object>> nodes = raft.getClusterStatus();
                if (nodes != null) {
                    System.out.println("WebServices: getClusterStatus returning " + nodes.size() + " nodes.");
                } else {
                    System.out.println("WebServices: getClusterStatus returning 'null' nodes list.");
                    nodes = new java.util.ArrayList<>();
                }

                List<Map<String, Object>> publicNodes = new java.util.ArrayList<>();
                for (Map<String, Object> node : nodes) {
                    Map<String, Object> pNode = new java.util.HashMap<>(node);
                    // Robust ID handling
                    String id = (String) pNode.get("_id");
                    if (id == null)
                        id = (String) pNode.get("id");

                    if (id != null) {
                        pNode.put("id", id);
                        // Ensure _id is also set for consistency
                        pNode.put("_id", id);
                    } else {
                        // Fallback if absolutely no ID found
                        String url = (String) pNode.get("url");
                        if (url != null) {
                            pNode.put("id", "node-" + url.hashCode());
                            pNode.put("_id", "node-" + url.hashCode());
                        }
                    }

                    // Add metrics if it's the current node
                    if (raft.getNodeId().equals(id)) {
                        String dataDir = (String) engine.getConfigManager().getOrDefault("DataDir", "data");
                        pNode.put("metrics", io.jettra.core.util.MetricsUtils.getSystemMetrics(dataDir));
                    } else if (id != null) {
                        // Try to get from cache
                        Map<String, Object> cached = PEER_METRICS_CACHE.get(id);
                        if (cached != null) {
                            pNode.put("metrics", cached);
                        }
                    }

                    publicNodes.add(pNode);
                }
                status.put("nodes", publicNodes);
            } else {
                status.put("enabled", false);
            }
            res.send(jsonMapper.writeValueAsString(status));
        } catch (Exception e) {
            e.printStackTrace();
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void stopNode(ServerRequest req, ServerResponse res) {
        try {
            if (engine.getRaftNode() == null) {
                res.status(Status.BAD_REQUEST_400).send("Raft not enabled");
                return;
            }

            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String nodeIdOrUrl = body.get("node");

            if (nodeIdOrUrl == null || nodeIdOrUrl.isEmpty()) {
                res.status(Status.BAD_REQUEST_400).send("Missing node ID or URL");
                return;
            }

            engine.getRaftNode().stopNode(nodeIdOrUrl);
            res.send(jsonMapper.createObjectNode().put("status", "stop_sent").toString());
        } catch (IllegalStateException e) {
            res.status(Status.BAD_REQUEST_400).send(e.getMessage());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deregisterNode(ServerRequest req, ServerResponse res) {
        try {
            if (engine.getRaftNode() == null) {
                res.status(Status.BAD_REQUEST_400).send("Raft not enabled");
                return;
            }

            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String url = body.get("url");

            if (url == null || url.isEmpty()) {
                res.status(Status.BAD_REQUEST_400).send("Missing url");
                return;
            }

            if (url.endsWith("/"))
                url = url.substring(0, url.length() - 1);

            engine.getRaftNode().deregisterNode(url);

            res.send(jsonMapper.createObjectNode().put("status", "deregistered").toString());
        } catch (IllegalStateException e) {
            res.status(Status.BAD_REQUEST_400).send(e.getMessage()); // Not Leader
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void commitTransaction(ServerRequest req, ServerResponse res) {
        try {
            String txID = req.query().get("txID");
            engine.getStore().commitTransaction(txID);
            res.send(jsonMapper.createObjectNode().put("status", "committed").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void rollbackTransaction(ServerRequest req, ServerResponse res) {
        try {
            String txID = req.query().get("txID");
            engine.getStore().rollbackTransaction(txID);
            res.send(jsonMapper.createObjectNode().put("status", "rolledback").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    // --- User Management ---
    private void listUsers(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            List<Map<String, Object>> users = engine.getStore().query("_system", "_users", null, 1000, 0);
            res.send(jsonMapper.writeValueAsString(users));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void createUser(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> user = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });

            String username = (String) user.get("username");
            if (username == null || username.isEmpty()) {
                res.status(Status.BAD_REQUEST_400).send("Username required");
                return;
            }

            // Check existing
            try {
                Map<String, Object> filter = java.util.Collections.singletonMap("username", username);
                List<Map<String, Object>> existing = engine.getStore().query("_system", "_users", filter, 1, 0);
                if (!existing.isEmpty()) {
                    res.status(Status.BAD_REQUEST_400).send("User already exists");
                    return;
                }
            } catch (Exception e) {
            } // ignore if query fails (e.g. collection missing)

            if (engine.getRaftNode() != null) {
                if (!engine.getRaftNode().isLeader()) {
                    res.status(Status.SERVICE_UNAVAILABLE_503).send("Not Leader");
                    return;
                }

                Map<String, Object> cmd = new java.util.HashMap<>();
                cmd.put("op", "save");
                cmd.put("db", "_system");
                cmd.put("col", "_users");
                cmd.put("doc", user);
                engine.getRaftNode().replicate(cmd);
            }

            engine.getStore().save("_system", "_users", user);
            res.send(jsonMapper.createObjectNode().put("status", "User created").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteUser(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            String id = req.query().get("id");
            if (engine.getRaftNode() != null) {
                if (!engine.getRaftNode().isLeader()) {
                    res.status(Status.SERVICE_UNAVAILABLE_503).send("Not Leader");
                    return;
                }
                Map<String, Object> cmd = new java.util.HashMap<>();
                cmd.put("op", "delete");
                cmd.put("db", "_system");
                cmd.put("col", "_users");
                cmd.put("id", id);
                engine.getRaftNode().replicate(cmd);
            }
            engine.getStore().delete("_system", "_users", id);
            res.send(jsonMapper.createObjectNode().put("status", "User deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private boolean requireAdmin(ServerRequest req, ServerResponse res) {
        // Simple check: authorize against _system with admin role
        // We need the username from auth header
        try {
            Optional<String> authHeader = req.headers().first(io.helidon.http.HeaderNames.AUTHORIZATION);
            if (authHeader.isEmpty()) {
                res.status(Status.UNAUTHORIZED_401).send("Unauthorized");
                return false;
            }

            String b64Credentials = authHeader.get().substring("Basic ".length());
            String credentials = new String(Base64.getDecoder().decode(b64Credentials));
            String username = credentials.split(":", 2)[0];

            if ("admin".equals(username))
                return true; // Backdoor/Root

            String role = engine.getAuth().getUserRole("_system", username);
            if ("admin".equals(role))
                return true;
        } catch (Exception e) {
            e.printStackTrace();
        }

        res.status(Status.FORBIDDEN_403).send("Forbidden: Admin Access Required");
        return false;
    }

    private void backupDatabase(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            String db = req.query().get("db");
            if (db == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db param");
                return;
            }
            String zipName = engine.getStore().backupDatabase(db);
            res.send(jsonMapper.createObjectNode().put("file", zipName).toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void listBackups(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            java.io.File backupsDir = new java.io.File("backups");
            if (!backupsDir.exists()) {
                res.send("[]");
                return;
            }
            String[] files = backupsDir.list((dir, name) -> name.endsWith(".zip"));
            res.send(jsonMapper.writeValueAsString(files));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void downloadBackup(ServerRequest req, ServerResponse res) {
        // Optional: Require auth? Yes.
        // But for browser download it might be tricky with headers.
        // For now, let's assume we can pass token in query param for download if
        // needed,
        // or just use basic auth if browser prompts.
        // Simplest: use same admin check (header based). Browser might need a small
        // turn around to send header.
        // Or we allow query param "token" for this specific endpoint.

        try {
            // Check auth from parameter if header missing (for direct browser links)
            String token = req.query().first("token").orElse(null);
            if (token != null) {
                // Validate token manually since middleware might have failed or skipped
                // Actually middleware runs before this. If middleware blocks missing header, we
                // can't use query param.
                // Let's rely on middleware. If accessing from browser, we might need a
                // fetch-blob flow or allow query-param-auth in middleware.
                // For now, assume fetch-blob flow in UI.
            }
            // Middleware should handle it if we send standard header.

            String filename = req.query().get("file");
            if (filename == null || filename.contains("..") || !filename.endsWith(".zip")) {
                res.status(Status.BAD_REQUEST_400).send("Invalid filename");
                return;
            }

            java.io.File file = new java.io.File("backups", filename);
            if (!file.exists()) {
                res.status(Status.NOT_FOUND_404).send("File not found");
                return;
            }

            res.headers().add(io.helidon.http.HeaderNames.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"");
            res.headers().add(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/zip");
            // send file content
            res.send(java.nio.file.Files.readAllBytes(file.toPath()));
            res.send(java.nio.file.Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void restoreDatabase(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            checkLeader(res);
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });

            String file = body.get("file");
            String db = body.get("db"); // Target DB

            if (file == null || db == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing file or db");
                return;
            }

            engine.getStore().restoreDatabase(file, db);
            res.send(jsonMapper.createObjectNode().put("status", "restored").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void restoreDatabaseFromUpload(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res))
            return;
        try {
            checkLeader(res);
            String db = req.query().get("db");
            if (db == null || db.isEmpty()) {
                res.status(Status.BAD_REQUEST_400).send("Missing db param");
                return;
            }

            // Read raw bytes from body
            // Note: For large files, streaming would be better, but for now loading into
            // memory is acceptable for this scale
            byte[] fileBytes = req.content().as(byte[].class);
            if (fileBytes.length == 0) {
                res.status(Status.BAD_REQUEST_400).send("Empty file");
                return;
            }

            // Save to backups dir
            java.nio.file.Path backupsDir = java.nio.file.Paths.get("backups");
            java.nio.file.Files.createDirectories(backupsDir);

            String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
            String filename = "uploaded_" + timestamp + ".zip";
            java.nio.file.Path tempZip = backupsDir.resolve(filename);

            java.nio.file.Files.write(tempZip, fileBytes);

            // Restore
            engine.getStore().restoreDatabase(filename, db);

            res.send(jsonMapper.createObjectNode()
                    .put("status", "restored")
                    .put("file", filename)
                    .toString());

        } catch (Exception e) {
            e.printStackTrace();
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void exportCollection(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String format = req.query().get("format"); // json or csv

            if (db == null || col == null || format == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db, col, or format");
                return;
            }

            // Get all documents
            List<Map<String, Object>> docs = engine.getStore().query(db, col, null, 1000000, 0); // Large limit

            String content = "";
            String contentType = "text/plain";
            String ext = "";

            if ("json".equalsIgnoreCase(format)) {
                content = jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(docs);
                contentType = "application/json";
                ext = "json";
            } else if ("csv".equalsIgnoreCase(format)) {
                content = convertToCSV(docs);
                contentType = "text/csv";
                ext = "csv";
            } else {
                res.status(Status.BAD_REQUEST_400).send("Invalid format. Use json or csv.");
                return;
            }

            res.headers().add(io.helidon.http.HeaderNames.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + col + "." + ext + "\"");
            res.headers().add(io.helidon.http.HeaderNames.CONTENT_TYPE, contentType);
            res.send(content);
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getVersions(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String id = req.query().get("id");

            if (db == null || col == null || id == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db, col, or id");
                return;
            }

            List<String> versions = engine.getStore().getVersions(db, col, id);
            res.send(jsonMapper.writeValueAsString(versions));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void restoreVersion(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });

            String db = body.get("db");
            String col = body.get("col");
            String id = body.get("id");
            String version = body.get("version");

            if (db == null || col == null || id == null || version == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing parameters");
                return;
            }

            engine.getStore().restoreVersion(db, col, id, version);
            res.send(jsonMapper.createObjectNode().put("status", "restored").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private String convertToCSV(List<Map<String, Object>> docs) {
        if (docs.isEmpty())
            return "";
        StringBuilder sb = new StringBuilder();
        // Header
        java.util.Set<String> keys = docs.get(0).keySet();
        sb.append(String.join(",", keys)).append("\n");
        // Rows
        for (Map<String, Object> doc : docs) {
            java.util.List<String> values = new java.util.ArrayList<>();
            for (String key : keys) {
                values.add(String.valueOf(doc.get(key)));
            }
            sb.append(String.join(",", values)).append("\n");
        }
        return sb.toString();
    }

    private void importCollection(ServerRequest req, ServerResponse res) {
        try {
            checkLeader(res);
            String db = req.query().get("db");
            String col = req.query().get("col");
            String format = req.query().get("format");

            if (db == null || col == null || format == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing db, col, or format");
                return;
            }

            byte[] bytes = req.content().as(byte[].class);
            String content = new String(bytes, java.nio.charset.StandardCharsets.UTF_8);

            int count = 0;
            boolean isLeader = (engine.getRaftNode() != null && engine.getRaftNode().isLeader());

            if ("json".equalsIgnoreCase(format)) {
                List<Map<String, Object>> docs = jsonMapper.readValue(content,
                        new TypeReference<List<Map<String, Object>>>() {
                        });
                for (Map<String, Object> doc : docs) {
                    if (isLeader) {
                        Map<String, Object> cmd = new java.util.HashMap<>();
                        cmd.put("op", "save");
                        cmd.put("db", db);
                        cmd.put("col", col);
                        cmd.put("doc", doc);
                        engine.getRaftNode().replicate(cmd);
                    }
                    engine.getStore().save(db, col, doc);
                    count++;
                }
            } else if ("csv".equalsIgnoreCase(format)) {
                List<Map<String, Object>> docs = parseCSV(content);
                for (Map<String, Object> doc : docs) {
                    if (isLeader) {
                        Map<String, Object> cmd = new java.util.HashMap<>();
                        cmd.put("op", "save");
                        cmd.put("db", db);
                        cmd.put("col", col);
                        cmd.put("doc", doc);
                        engine.getRaftNode().replicate(cmd);
                    }
                    engine.getStore().save(db, col, doc);
                    count++;
                }
            } else {
                res.status(Status.BAD_REQUEST_400).send("Invalid format");
                return;
            }

            res.send(jsonMapper.createObjectNode().put("status", "imported").put("count", count).toString());

        } catch (Exception e) {
            e.printStackTrace();
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private List<Map<String, Object>> parseCSV(String content) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        String[] lines = content.split("\n");
        if (lines.length == 0)
            return result;

        String[] headers = lines[0].trim().split(","); // Simplistic header split
        // Trim headers
        for (int i = 0; i < headers.length; i++)
            headers[i] = headers[i].trim();

        // Simple parsing not handling quoted commas correctly for now as per minimal
        // requirement,
        // but strictly we should use a proper parser.
        // Let's at least handle quotes if possible strictly or warn.
        // Implementing a quick robust splitter:

        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty())
                continue;

            // Split by comma respecting quotes
            List<String> tokens = splitCSVLine(line);

            Map<String, Object> doc = new java.util.HashMap<>();
            for (int j = 0; j < headers.length && j < tokens.size(); j++) {
                String val = tokens.get(j);
                if (!val.isEmpty()) {
                    // Try number
                    try {
                        if (val.contains("."))
                            doc.put(headers[j], Double.parseDouble(val));
                        else
                            doc.put(headers[j], Long.parseLong(val));
                    } catch (NumberFormatException e) {
                        doc.put(headers[j], val);
                    }
                }
            }
            result.add(doc);
        }
        return result;
    }

    private List<String> splitCSVLine(String line) {
        List<String> tokens = new java.util.ArrayList<>();
        StringBuilder sb = new StringBuilder();
        boolean inQuotes = false;
        for (char c : line.toCharArray()) {
            if (c == '\"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                tokens.add(sb.toString().trim());
                sb.setLength(0);
            } else {
                sb.append(c);
            }
        }
        tokens.add(sb.toString().trim());
        return tokens;
    }

    private void getVersionContent(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String id = req.query().get("id");
            String version = req.query().get("version");
            Map<String, Object> content = engine.getStore().getVersionContent(db, col, id, version);
            if (content != null) {
                res.send(jsonMapper.writeValueAsString(content));
            } else {
                res.status(Status.NOT_FOUND_404).send("Version not found");
            }
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getMetrics(ServerRequest req, ServerResponse res) {
        try {
            String dataDir = (String) engine.getConfigManager().getOrDefault("DataDir", "data");
            res.send(jsonMapper.writeValueAsString(io.jettra.core.util.MetricsUtils.getSystemMetrics(dataDir)));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getFederatedStatus(ServerRequest req, ServerResponse res) {
        try {
            List<String> fedServers = (List<String>) engine.getConfigManager().getOrDefault("FederatedServers",
                    java.util.Collections.emptyList());
            if (fedServers.isEmpty()) {
                res.status(Status.NOT_FOUND_404).send("Federated mode not configured");
                return;
            }

            // Simple proxy to the first federated server for now
            String fedUrl = fedServers.get(0);
            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(fedUrl + "/federated/status"))
                    .GET()
                    .timeout(java.time.Duration.ofMillis(2000))
                    .build();

            engine.getRaftNode().getHttpClientProxy()
                    .sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        res.send(response.body());
                    });
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void restartNode(ServerRequest req, ServerResponse res) {
        try {
            res.send(jsonMapper.createObjectNode().put("status", "restarting").toString());
            // Small delay to allow response to be sent
            new Thread(() -> {
                try {
                    Thread.sleep(1000);
                    LOGGER.info("Restart command received. Exiting JVM...");
                    System.exit(3);
                } catch (Exception e) {
                }
            }).start();
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void getConfig(ServerRequest req, ServerResponse res) {
        try {
            Map<String, Object> config = engine.getConfigManager().getConfigMap();
            res.headers().add(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/json");
            res.send(jsonMapper.writerWithDefaultPrettyPrinter().writeValueAsString(config));
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Error reading config", e);
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void saveConfig(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> newConfig = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });

            io.jettra.core.config.ConfigManager manager = engine.getConfigManager();
            Map<String, Object> currentConfig = manager.getConfigMap();
            synchronized (manager) {
                currentConfig.clear();
                currentConfig.putAll(newConfig);
                manager.save();
            }

            boolean restart = req.query().first("restart").map(Boolean::parseBoolean).orElse(false);
            if (restart) {
                res.send(jsonMapper.createObjectNode().put("status", "restarting").toString());
                new Thread(() -> {
                    try {
                        Thread.sleep(1000);
                        LOGGER.info("Config saved with restart. Exiting JVM...");
                        System.exit(3);
                    } catch (Exception e) {
                    }
                }).start();
            } else {
                res.send(jsonMapper.createObjectNode().put("status", "success").toString());
            }
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }
}
