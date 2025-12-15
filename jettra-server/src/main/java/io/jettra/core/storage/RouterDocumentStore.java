package io.jettra.core.storage;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Routes storage operations to the appropriate storage engine (Basic or Optimized)
 * based on the database configuration.
 */
public class RouterDocumentStore implements DocumentStore {

    private final String dataDirectory;
    private final JettraBasicStore basicStore;
    private final JettraEngineStore engineStore;
    private final Map<String, DocumentStore> cache = new ConcurrentHashMap<>();

    // Reserved collections that might always use basic store? No, better to follow DB engine.
    // Exception: getting the engine metadata itself.

    public RouterDocumentStore(String dataDirectory) throws Exception {
        this.dataDirectory = dataDirectory;
        this.basicStore = new JettraBasicStore(dataDirectory);
        this.engineStore = new JettraEngineStore(dataDirectory);
    }

    public void setValidator(io.jettra.core.validation.Validator validator) {
        this.basicStore.setValidator(validator);
        this.engineStore.setValidator(validator);
    }

    private DocumentStore getStore(String database) {
        if (database == null) return basicStore;
        
        return cache.computeIfAbsent(database, db -> {
            try {
                // Determine engine for this database
                // Strategy: Check if _engine collection exists and contains metadata
                // We use basicStore to read it because we enforce metadata to be loose/basic?
                // Or we check if the _engine folder exists.
                // If the user created with "JettraEngineStore", we supposedly created _engine collection.
                // But where did we write it?
                // If we wrote it with BasicStore, we can read it with BasicStore.
                
                // Let's assume we ALWAYS write _engine metadata using BasicStore (JSON/CBOR) so it's easily readable.
                
                List<Map<String, Object>> engines = basicStore.query(db, "_engine", null, 1, 0);
                if (!engines.isEmpty()) {
                    String engineName = (String) engines.get(0).get("name");
                    if ("JettraEngineStore".equals(engineName)) {
                        return engineStore;
                    }
                }
                
                // Default to Basic
                return basicStore;
            } catch (Exception e) {
                e.printStackTrace();
                return basicStore;
            }
        });
    }

    @Override
    public void createDatabase(String name, String engine) throws Exception {
        // Create directory
        basicStore.createDatabase(name, engine); // Just mkdirs
        
        // Save metadata
        // We force metadata to be saved in BasicStore for bootstrapping
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("name", engine != null ? engine : "JettraBasicStore");
        metadata.put("created_at", System.currentTimeMillis());
        
        basicStore.save(name, "_engine", metadata);
        
        // Invalidate cache implicitly or explicit update
        cache.remove(name);
    }

    // --- Delegation ---

    @Override
    public String save(String database, String collection, Map<String, Object> document) throws Exception {
        return getStore(database).save(database, collection, document);
    }

    @Override
    public Map<String, Object> findByID(String database, String collection, String id) throws Exception {
        return getStore(database).findByID(database, collection, id);
    }

    @Override
    public List<Map<String, Object>> query(String database, String collection, Map<String, Object> filter, int limit,
            int offset) throws Exception {
        return getStore(database).query(database, collection, filter, limit, offset);
    }

    @Override
    public void update(String database, String collection, String id, Map<String, Object> document) throws Exception {
        getStore(database).update(database, collection, id, document);
    }

    @Override
    public void delete(String database, String collection, String id) throws Exception {
        getStore(database).delete(database, collection, id);
    }

    @Override
    public int count(String database, String collection) throws Exception {
        return getStore(database).count(database, collection);
    }

    @Override
    public Map<String, List<String>> getDatabaseStructure() throws Exception {
        // Structure is filesystem based, basicStore impl returns directory structure which is same for both
        return basicStore.getDatabaseStructure();
    }

    @Override
    public void renameDatabase(String oldName, String newName) throws Exception {
        // Physical move
        basicStore.renameDatabase(oldName, newName); // filesystem move
        cache.remove(oldName);
        cache.remove(newName);
    }

    @Override
    public void deleteDatabase(String name) throws Exception {
        basicStore.deleteDatabase(name); // filesystem delete
        cache.remove(name);
    }

    @Override
    public String backupDatabase(String database) throws Exception {
        // Provide backup of raw files. 
        // As long as restore puts them back, it should be fine.
        // If engineStore uses .jdb files (but binary content), zip will contain them.
        // Restore will unzip them. 
        // Logic defaults to basicStore implementation (file walk and zip) which is generic.
        return basicStore.backupDatabase(database);
    }

    @Override
    public void restoreDatabase(String zipFilename, String targetDatabase) throws Exception {
        // Restore is also generic unzipping
        basicStore.restoreDatabase(zipFilename, targetDatabase);
        cache.remove(targetDatabase);
    }

    @Override
    public List<String> getVersions(String database, String collection, String id) throws Exception {
        return getStore(database).getVersions(database, collection, id);
    }

    @Override
    public void restoreVersion(String database, String collection, String id, String version) throws Exception {
        getStore(database).restoreVersion(database, collection, id, version);
    }

    @Override
    public Map<String, Object> getVersionContent(String database, String collection, String id, String version)
            throws Exception {
        return getStore(database).getVersionContent(database, collection, id, version);
    }

    @Override
    public long getNextSequence(String database, String collection, String field) throws Exception {
        return getStore(database).getNextSequence(database, collection, field);
    }

    @Override
    public void createCollection(String database, String collection) throws Exception {
        getStore(database).createCollection(database, collection);
    }

    @Override
    public void renameCollection(String database, String oldName, String newName) throws Exception {
        getStore(database).renameCollection(database, oldName, newName);
    }

    @Override
    public void deleteCollection(String database, String collection) throws Exception {
        getStore(database).deleteCollection(database, collection);
    }

    @Override
    public String beginTransaction() throws Exception {
        // Transactions are global (system) usually, or per DB?
        // BasicStore implementation puts them in _system/_transactions.
        // _system usually falls back to basic store.
        return basicStore.beginTransaction();
    }

    @Override
    public void commitTransaction(String txID) throws Exception {
        // Replay log. 
        // The log contains "db" and "col". 
        // We need to route each op to the correct store!
        // basicStore.commitTransaction tries to replay using `this.save`.
        // If we use basicStore.commitTransaction, it uses basicStore.save/delete.
        // This is WRONG if the target DB is EngineStore.
        
        // We must implement commitTransaction logic here to dispatch ops correctly.
        // Reuse logic from BasicStore but dispatch.
        
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        if (!Files.exists(txDir)) {
            throw new Exception("Transaction " + txID + " not found or already completed.");
        }

        try {
            // Need serialization to read the log?
            // Logs are written by saveTx. 
            // SaveTx uses the store of that DB? No, saveTx writes to _system location.
            // Who writes saveTx? `getStore(db).saveTx`?
            // If getStore(db) is EngineStore, it writes binary log.
            // If getStore(db) is BasicStore, it writes CBOR log.
            
            // This is messy. Transaction logs should use a consistent format or we need to know how to read them.
            // PROPOSAL: Always use BasicStore (CBOR) for Transaction Logs in _system.
            // So `saveTx` and `deleteTx` should usually go to BasicStore?
            // Yes, let's enforce routing saveTx/deleteTx to BasicStore always? 
            // Or does EngineStore have optimized logging?
            // The user didn't ask for optimized logs, just optimized storage.
            // For simplicity and reliability, let's use BasicStore for all transaction management (WAL).
            
            // basicStore.commitTransactionRouter(txID, this);  -- Removed, using manual replay below 
            // Wait, basicStore doesn't have `commitTransactionRouter`.
            // I need to duplicate the loop here or expose a way.
            // Duplicating the loop is safer.
            
            // Note: Use basicStore to READ the logs because we will force WRITE to basicStore.
            // See saveTx override below.
            
            // Actually, I can't easily access `JettraBasicStore.readMap` (private).
            // But `JettraBinarySerialization` is public if I used that... but BasicStore uses Jackson.
            // I should assume logs are Jackson/CBOR if I use BasicStore for them.
            // So I need to use `new ObjectMapper(new CBORFactory())` here to read them?
            // OR I can add a `readTxLog` to DocumentStore? No.
            
            // Let's implement logic here.
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper(new com.fasterxml.jackson.dataformat.cbor.CBORFactory());
            
             try (java.util.stream.Stream<Path> files = Files.list(txDir)) {
                List<Path> ops = files.sorted().toList();
                for (Path opFile : ops) {
                    Map<String, Object> opData = mapper.readValue(opFile.toFile(), new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
                    String type = (String) opData.get("type");
                    String db = (String) opData.get("db");
                    String col = (String) opData.get("col");
                    
                    if ("save".equals(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) opData.get("doc");
                        // Route to correct store!
                        this.save(db, col, doc);
                    } else if ("delete".equals(type)) {
                        String id = (String) opData.get("id");
                        this.delete(db, col, id);
                    }
                }
            }
            
        } finally {
            try {
                rollbackTransaction(txID);
            } catch (Exception e) {}
        }
    }

    @Override
    public void rollbackTransaction(String txID) throws Exception {
        basicStore.rollbackTransaction(txID);
    }

    @Override
    public void saveTx(String database, String collection, Map<String, Object> document, String txID) throws Exception {
        // ALWAYS use basic store for logs
        basicStore.saveTx(database, collection, document, txID);
    }

    @Override
    public void deleteTx(String database, String collection, String id, String txID) throws Exception {
        // ALWAYS use basic store for logs
        basicStore.deleteTx(database, collection, id, txID);
    }

    @Override
    public String getDatabaseEngine(String database) throws Exception {
        return getStore(database).getDatabaseEngine(database);
    }
}
