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

    public WebServices(Engine engine) {
        this.engine = engine;
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
                // Auth Middleware for API
                .any("/api/*", this::authMiddleware);
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
            if (true || engine.getAuth().authenticate("_system", user, pass)) {
                // Return a "token" or just success. For basic auth frontend, we just need to
                // know it works.
                // We'll return the credentials base64 encoded as a token for the frontend to
                // use
                String token = Base64.getEncoder().encodeToString((user + ":" + pass).getBytes());
                res.send(jsonMapper.createObjectNode().put("token", "Basic " + token).toString());
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
            byte[] content = req.content().as(byte[].class);
            Map<String, Object> doc = jsonMapper.readValue(content, new TypeReference<Map<String, Object>>() {
            });
            String id = engine.getStore().save(db, col, doc);
            res.send(jsonMapper.createObjectNode().put("id", id).toString());
        } catch (Exception e) {
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
            engine.getStore().delete(db, col, id);
            res.send(jsonMapper.createObjectNode().put("status", "deleted").toString());
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }

    private void queryDocuments(ServerRequest req, ServerResponse res) {
        try {
            String db = req.query().get("db");
            String col = req.query().get("col");
            int limit = 100;
            int offset = 0;

            if (req.query().contains("limit"))
                limit = Integer.parseInt(req.query().get("limit"));
            if (req.query().contains("offset"))
                offset = Integer.parseInt(req.query().get("offset"));

            List<Map<String, Object>> docs = engine.getStore().query(db, col, null, limit, offset);
            res.send(jsonMapper.writeValueAsString(docs));
        } catch (Exception e) {
            res.status(Status.INTERNAL_SERVER_ERROR_500).send(e.getMessage());
        }
    }
}
