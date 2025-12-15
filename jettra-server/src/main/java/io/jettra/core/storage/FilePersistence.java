package io.jettra.core.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.jettra.core.validation.Validator;

public class FilePersistence implements DocumentStore {
    private final String dataDirectory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // Use CBOR Factory for binary storage
    private final ObjectMapper mapper = new ObjectMapper(new CBORFactory());
    private Validator validator;

    public FilePersistence(String dataDirectory) throws Exception {
        this.dataDirectory = dataDirectory;
        Files.createDirectories(Paths.get(dataDirectory));
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    @Override
    public String save(String database, String collection, Map<String, Object> document) throws Exception {
        lock.writeLock().lock();
        try {
            if (validator != null) {
                validator.validate(database, collection, document);
            }
            // Document is already a Map, no need to parse from bytes

            String id = (String) document.get("_id");
            if (id == null) {
                id = (String) document.get("id");
            }
            if (id == null) {
                id = UUID.randomUUID().toString();
                document.put("_id", id);
            }

            Path collectionDir = Paths.get(dataDirectory, database, collection);
            Files.createDirectories(collectionDir);

            Path filePath = collectionDir.resolve(id + ".jdb");
            // Write Map as CBOR
            mapper.writeValue(filePath.toFile(), document);

            return id;
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Map<String, Object> findByID(String database, String collection, String id) throws Exception {
        lock.readLock().lock();
        try {
            Path filePath = Paths.get(dataDirectory, database, collection, id + ".jdb");
            if (!Files.exists(filePath)) {
                return null;
            }
            // Read CBOR to Map
            return mapper.readValue(filePath.toFile(), new TypeReference<Map<String, Object>>() {
            });
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public List<Map<String, Object>> query(String database, String collection, Map<String, Object> filter, int limit,
            int offset)
            throws Exception {
        lock.readLock().lock();
        try {
            Path collectionDir = Paths.get(dataDirectory, database, collection);
            if (!Files.exists(collectionDir)) {
                return new ArrayList<>();
            }

            List<Map<String, Object>> results = new ArrayList<>();
            try (Stream<Path> paths = Files.list(collectionDir)) {
                List<Path> files = paths.filter(p -> p.toString().endsWith(".jdb") && !p.getFileName().toString().equals("_indexes.jdb")).toList();

                int skipped = 0;
                for (Path file : files) {
                    // Optimization: if no filter, skip reading file content for offset
                    if (filter == null || filter.isEmpty()) {
                        if (skipped < offset) {
                            skipped++;
                            continue;
                        }
                    }

                    Map<String, Object> docMap = null;
                    try {
                        docMap = mapper.readValue(file.toFile(), new TypeReference<Map<String, Object>>() {});
                    } catch (Exception e) {
                        System.err.println("Skipping corrupted file: " + file.getFileName() + " - " + e.getMessage());
                        continue;
                    }

                    if (filter != null && !filter.isEmpty()) {
                        boolean match = true;
                        for (Map.Entry<String, Object> entry : filter.entrySet()) {
                            if (!entry.getValue().equals(docMap.get(entry.getKey()))) {
                                match = false;
                                break;
                            }
                        }
                        if (!match)
                            continue;

                        // If matched, apply offset logic here
                        if (skipped < offset) {
                            skipped++;
                            continue;
                        }
                    }

                    results.add(docMap);
                    if (limit > 0 && results.size() >= limit) {
                        break;
                    }
                }
            }
            return results;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void update(String database, String collection, String id, Map<String, Object> document) throws Exception {
        save(database, collection, document);
    }

    @Override
    public void delete(String database, String collection, String id) throws Exception {
        lock.writeLock().lock();
        try {
            if (validator != null) {
                // We need the document to validate deletion
                Map<String, Object> document = findByID(database, collection, id);
                if (document != null) {
                     // Pass collection name just in case needed context (passed as arg)
                     // document.put("_collection_temp_name_", collection); // Cleaner to rely on arg
                     validator.validateDelete(database, collection, document);
                }
            }
            Path filePath = Paths.get(dataDirectory, database, collection, id + ".jdb");
            Files.deleteIfExists(filePath);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public int count(String database, String collection) throws Exception {
        lock.readLock().lock();
        try {
            Path collectionDir = Paths.get(dataDirectory, database, collection);
            if (!Files.exists(collectionDir))
                return 0;
            try (Stream<Path> paths = Files.list(collectionDir)) {
                return (int) paths.filter(p -> p.toString().endsWith(".jdb")).count();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public Map<String, List<String>> getDatabaseStructure() throws Exception {
        lock.readLock().lock();
        try {
            Map<String, List<String>> structure = new HashMap<>();
            Path root = Paths.get(dataDirectory);
            if (!Files.exists(root))
                return structure;

            try (Stream<Path> dbs = Files.list(root)) {
                for (Path db : dbs.toList()) {
                    if (Files.isDirectory(db)) {
                        String dbName = db.getFileName().toString();
                        List<String> cols = new ArrayList<>();
                        try (Stream<Path> collections = Files.list(db)) {
                            for (Path col : collections.toList()) {
                                if (Files.isDirectory(col)) {
                                    cols.add(col.getFileName().toString());
                                }
                            }
                        }
                        structure.put(dbName, cols);
                    }
                }
            }
            return structure;
        } finally {
            lock.readLock().unlock();
        }
    }

    // Implement dummy methods for the rest of the interface for now
    @Override
    public void renameDatabase(String oldName, String newName) throws Exception {
        lock.writeLock().lock();
        try {
            Path oldDir = Paths.get(dataDirectory, oldName);
            Path newDir = Paths.get(dataDirectory, newName);
            if (Files.exists(oldDir)) {
                Files.move(oldDir, newDir);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void deleteDatabase(String name) throws Exception {
        lock.writeLock().lock();
        try {
            Path dir = Paths.get(dataDirectory, name);
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String backupDatabase(String database) throws Exception {
        lock.readLock().lock();
        try {
            Path dbDir = Paths.get(dataDirectory, database);
            if (!Files.exists(dbDir)) {
                throw new Exception("Database " + database + " does not exist");
            }

            Path backupsDir = Paths.get("backups");
            Files.createDirectories(backupsDir);

            String timestamp = new java.text.SimpleDateFormat("yyyyMMddHHmmss").format(new java.util.Date());
            String zipName = database + "_" + timestamp + ".zip";
            Path zipPath = backupsDir.resolve(zipName);

            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(Files.newOutputStream(zipPath))) {
                try (Stream<Path> walk = Files.walk(dbDir)) {
                    walk.filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            java.util.zip.ZipEntry zipEntry = new java.util.zip.ZipEntry(dbDir.relativize(path).toString());
                            try {
                                zos.putNextEntry(zipEntry);
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (Exception e) {
                                System.err.println("Failed to zip file: " + path);
                            }
                        });
                }
            }
            return zipName;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public long getNextSequence(String database, String collection, String field) throws Exception {
        return 0;
    }

    @Override
    public void createCollection(String database, String collection) throws Exception {
        lock.writeLock().lock();
        try {
            Path dir = Paths.get(dataDirectory, database, collection);
            Files.createDirectories(dir);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void renameCollection(String database, String oldName, String newName) throws Exception {
    }

    @Override
    public void deleteCollection(String database, String collection) throws Exception {
        lock.writeLock().lock();
        try {
            Path dir = Paths.get(dataDirectory, database, collection);
            if (Files.exists(dir)) {
                try (Stream<Path> walk = Files.walk(dir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public String beginTransaction() throws Exception {
        String txID = UUID.randomUUID().toString();
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        Files.createDirectories(txDir);
        return txID;
    }

    @Override
    public void commitTransaction(String txID) throws Exception {
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        if (!Files.exists(txDir)) {
            throw new Exception("Transaction " + txID + " not found or already completed.");
        }

        try {
            // Read all ops and sort by filename (timestamp)
            try (Stream<Path> files = Files.list(txDir)) {
                List<Path> ops = files.sorted().toList();
                
                // Replay
                for (Path opFile : ops) {
                    Map<String, Object> opData = mapper.readValue(opFile.toFile(), new TypeReference<Map<String, Object>>() {});
                    String type = (String) opData.get("type");
                    String db = (String) opData.get("db");
                    String col = (String) opData.get("col");
                    
                    if ("save".equals(type)) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> doc = (Map<String, Object>) opData.get("doc");
                        save(db, col, doc);
                    } else if ("delete".equals(type)) {
                        String id = (String) opData.get("id");
                        delete(db, col, id);
                    }
                }
            }
        } finally {
            // Cleanup: Rollback/Cleanup logs even if replay failed partial?
            // "Atomicity": If replay fails halfway, we are in inconsistent state.
            // A real WAL would redo on startup. supporting that requires loading logs on startup.
            // For now, we just delete the log after attempt.
            rollbackTransaction(txID); 
        }
    }

    @Override
    public void rollbackTransaction(String txID) throws Exception {
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        if (Files.exists(txDir)) {
             try (Stream<Path> walk = Files.walk(txDir)) {
                 walk.sorted(java.util.Comparator.reverseOrder())
                     .map(Path::toFile)
                     .forEach(File::delete);
             }
        }
    }

    @Override
    public void saveTx(String database, String collection, Object document, String txID) throws Exception {
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        if (!Files.exists(txDir)) throw new Exception("Transaction " + txID + " not active");
        
        Map<String, Object> op = new HashMap<>();
        op.put("type", "save");
        op.put("db", database);
        op.put("col", collection);
        op.put("doc", document);
        
        // Use nanotime for ordering
        String filename = System.nanoTime() + ".op";
        mapper.writeValue(txDir.resolve(filename).toFile(), op);
    }

    @Override
    public void deleteTx(String database, String collection, String id, String txID) throws Exception {
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        if (!Files.exists(txDir)) throw new Exception("Transaction " + txID + " not active");

        Map<String, Object> op = new HashMap<>();
        op.put("type", "delete");
        op.put("db", database);
        op.put("col", collection);
        op.put("id", id);
        
        String filename = System.nanoTime() + ".op";
        mapper.writeValue(txDir.resolve(filename).toFile(), op);
    }
}
