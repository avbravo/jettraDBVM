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
    private final Engine engine;
    private final ObjectMapper jsonMapper = new ObjectMapper();
    private final QueryExecutor queryExecutor;

    public WebServices(Engine engine) {
        this.engine = engine;
        this.queryExecutor = new QueryExecutor(engine);
    }

    public void register(HttpRules rules) {
        rules
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
                // Transactions
                .post("/api/tx/begin", this::beginTransaction)
                .post("/api/tx/commit", this::commitTransaction)
                .post("/api/tx/rollback", this::rollbackTransaction)
                // Auth Middleware for API
                .any("/api/*", this::authMiddleware);

        
        // Register Raft Services
        // Note: RaftService registers its own paths (e.g. /raft/...) which are NOT under /api/
        // If we want them secured or under /api, we should change RaftService or wrap here.
        // For now, let's just register them.
        engine.getRaftService().register(rules);

        // User Management
        rules
            .get("/api/users", this::listUsers)
            .post("/api/users", this::createUser)
            .delete("/api/users", this::deleteUser);
    }

    private void authMiddleware(ServerRequest req, ServerResponse res) {
        // Skip login endpoint
        if (req.path().path().equals("/api/login")) {
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
        String db = req.query().get("db");
        if (db == null || db.equals("admin"))
            db = "_system";

        boolean isBackdoor = "admin".equals(user) && "admin".equals(pass);
        if (!isBackdoor && !engine.getAuth().authenticate(db, user, pass)) {
            res.status(Status.UNAUTHORIZED_401).send("Invalid credentials");
            return;
        }

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
            if (("admin".equals(user) && "admin".equals(pass)) || engine.getAuth().authenticate("_system", user, pass)) {
                // Return a "token" or just success. For basic auth frontend, we just need to
                // know it works.
                // We'll return the credentials base64 encoded as a token for the frontend to
                // use
                String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                String role = engine.getAuth().getUserRole("_system", user);
                if (role == null && "admin".equals(user)) role = "admin"; // Fallback for backdoor

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
        // We can assume engine or storage has a method for this, or we implement it
        // here
        try {
            java.io.File root = new java.io.File("data"); // Hardcoded for now based on config
            String[] dbs = root.list((dir, name) -> new java.io.File(dir, name).isDirectory());
            res.send(jsonMapper.writeValueAsString(dbs));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void createDatabase(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String db = body.get("name");
            // To create a DB folder, we can create a dummy collection or just mkdir.
            // DocumentStore interface has createCollection.
            // Let's create a _schema or _info collection to initialize it?
            // Or just rely on the fact that creating a collection creates the DB.
            engine.getStore().createCollection(db, "_info");
            engine.getStore().createCollection(db, "_rules");
            res.send(jsonMapper.createObjectNode().put("status", "created").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteDatabase(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("name");
            engine.getStore().deleteDatabase(db);
            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void renameDatabase(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String oldName = body.get("oldName");
            String newName = body.get("newName");
            engine.getStore().renameDatabase(oldName, newName);
            res.send(jsonMapper.createObjectNode().put("status", "renamed").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void createCollection(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String db = body.get("database");
            String col = body.get("collection");
            engine.getStore().createCollection(db, col);
            res.send(jsonMapper.createObjectNode().put("status", "created").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteCollection(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("database");
            String col = req.query().get("collection");
            engine.getStore().deleteCollection(db, col);
            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void renameCollection(ServerRequest req, ServerResponse res) {
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {
            });
            String db = body.get("database");
            String oldName = body.get("oldName");
            String newName = body.get("newName");
            engine.getStore().renameCollection(db, oldName, newName);
            res.send(jsonMapper.createObjectNode().put("status", "renamed").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void listCollections(ServerRequest req, ServerResponse res) {
        String db = req.path().pathParameters().get("db");
        try {
            java.io.File dbDir = new java.io.File("data/" + db);
            String[] cols = dbDir.list((dir, name) -> new java.io.File(dir, name).isDirectory());
            res.send(jsonMapper.writeValueAsString(cols));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void saveDocument(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            String txID = req.query().first("tx").orElse(null);
            System.out.println("DEBUG: saveDocument called for " + db + "/" + col + (txID != null ? " [TX: " + txID + "]" : ""));

            byte[] content = req.content().as(byte[].class);
            Map<String, Object> doc = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            
            String id;
            if (txID != null && !txID.isEmpty()) {
                // Determine ID if not present (client should send it or we gen it)
                // For tx save, we might want to return ID. But saveTx is void.
                // We should generate ID here if missing.
                id = (String) doc.get("_id");
                if (id == null) {
                    id = java.util.UUID.randomUUID().toString();
                    doc.put("_id", id);
                }
                engine.getStore().saveTx(db, col, doc, txID);
            } else {
                id = engine.getStore().save(db, col, doc);
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

    private void executeCommand(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db"); // Context DB
            if (db == null) db = "test"; // Default
            
            byte[] content = req.content().as(byte[].class);
            Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {});
            String cmd = body.get("command");
            
            Object result = queryExecutor.execute(db, cmd);
            
            if (result instanceof String) {
                 res.send(jsonMapper.createObjectNode().put("message", (String)result).toString());
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
            Map<String, Object> body = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            
            String db = (String) body.get("database");
            String col = (String) body.get("collection");
            String field = (String) body.get("field");
            Boolean unique = (Boolean) body.get("unique");
            Boolean sequential = (Boolean) body.get("sequential");
            if (unique == null) unique = false;
            if (sequential == null) sequential = false;
            
            if (db == null || col == null || field == null) {
                res.status(Status.BAD_REQUEST_400).send("Missing field, database, or collection");
                return;
            }
            
            List<String> fields = java.util.Arrays.asList(field.split(","));
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
            
            List<String> fields = java.util.Arrays.asList(field.split(","));
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
        if (!requireAdmin(req, res)) return;
        try {
            List<Map<String, Object>> users = engine.getStore().query("_system", "_users", null, 1000, 0);
            res.send(jsonMapper.writeValueAsString(users));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void createUser(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res)) return;
        try {
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> user = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {});
            
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
            } catch (Exception e) {} // ignore if query fails (e.g. collection missing)
            
            engine.getStore().save("_system", "_users", user);
            res.send(jsonMapper.createObjectNode().put("status", "User created").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void deleteUser(ServerRequest req, ServerResponse res) {
        if (!requireAdmin(req, res)) return;
        try {
            String id = req.query().get("id");
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
            
            if ("admin".equals(username)) return true; // Backdoor/Root

            String role = engine.getAuth().getUserRole("_system", username);
            if ("admin".equals(role)) return true;
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        res.status(Status.FORBIDDEN_403).send("Forbidden: Admin Access Required");
        return false;
    }

    private void backupDatabase(ServerRequest req, ServerResponse res) {
         if (!requireAdmin(req, res)) return;
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
        if (!requireAdmin(req, res)) return;
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
        // For now, let's assume we can pass token in query param for download if needed,
        // or just use basic auth if browser prompts.
        // Simplest: use same admin check (header based). Browser might need a small turn around to send header.
        // Or we allow query param "token" for this specific endpoint.
        
        try {
             // Check auth from parameter if header missing (for direct browser links)
             String token = req.query().get("token");
             if (token != null) {
                  // Validate token manually since middleware might have failed or skipped
                  // Actually middleware runs before this. If middleware blocks missing header, we can't use query param.
                  // Let's rely on middleware. If accessing from browser, we might need a fetch-blob flow or allow query-param-auth in middleware.
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
             
             res.headers().add(io.helidon.http.HeaderNames.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"");
             res.headers().add(io.helidon.http.HeaderNames.CONTENT_TYPE, "application/zip");
             // send file content
             res.send(java.nio.file.Files.readAllBytes(file.toPath()));
             res.send(java.nio.file.Files.readAllBytes(file.toPath()));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void restoreDatabase(ServerRequest req, ServerResponse res) {
         if (!requireAdmin(req, res)) return;
         try {
             byte[] content = req.content().as(byte[].class);
             Map<String, String> body = jsonMapper.readValue(content, new TypeReference<Map<String, String>>() {});
             
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
}
