package io.jettra.core.storage;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
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
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import io.jettra.core.validation.Validator;

/**
 * Storage engine implementation optimized for Java objects using JettraBinarySerialization.
 */
public class JettraEngineStore implements DocumentStore {
    private final String dataDirectory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private Validator validator;
    private final ObjectMapper cborMapper = new ObjectMapper(new CBORFactory());

    public JettraEngineStore(String dataDirectory) throws Exception {
        this.dataDirectory = dataDirectory;
        Files.createDirectories(Paths.get(dataDirectory));
    }

    // LRU Cache: capacity 5000 items
    private final Map<String, Map<String, Object>> cache = java.util.Collections.synchronizedMap(
        new java.util.LinkedHashMap<String, Map<String, Object>>(5000, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Map<String, Object>> eldest) {
                return size() > 5000;
            }
        });
        
    private String getCacheKey(String db, String col, String id) {
        return db + "/" + col + "/" + id;
    }

    public void setValidator(Validator validator) {
        this.validator = validator;
    }

    private void writeMap(Path path, Map<String, Object> map) throws Exception {
        // Adaptive compression: Serialize to byte array first
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        try (DataOutputStream dos = new DataOutputStream(baos)) {
            JettraBinarySerialization.serialize(map, dos);
        }
        byte[] data = baos.toByteArray();
        
        // Threshold 512 bytes
        try (java.io.OutputStream fileOut = new java.io.BufferedOutputStream(Files.newOutputStream(path), 65536);
             DataOutputStream out = new DataOutputStream(fileOut)) {
             
             if (data.length > 512) {
                 // GZIP Magic will be written by GZIPOutputStream (0x1f 0x8b)
                 // NOTE: GZIPOutputStream writes header.
                 // We just wrap it.
                 try (GZIPOutputStream gzip = new GZIPOutputStream(out) {
                     {
                         def.setLevel(java.util.zip.Deflater.BEST_SPEED); // Optimize speed
                     }
                 }) {
                    gzip.write(data);
                 }
             } else {
                 // Write Uncompressed Magic: 0x00 0x00 (2 bytes to avoid confusion with GZIP 0x1f 0x8b or CBOR)
                 out.writeByte(0x00);
                 out.writeByte(0x00);
                 out.write(data);
             }
        }
    }

    private Map<String, Object> readMap(Path path) throws Exception {
        try (java.io.InputStream fileIn = Files.newInputStream(path);
             java.io.BufferedInputStream bufIn = new java.io.BufferedInputStream(fileIn, 65536)) {
             
             bufIn.mark(10);
             int b1 = bufIn.read();
             int b2 = bufIn.read();
             bufIn.reset();
             
             if (b1 == 0x1f && b2 == 0x8b) {
                 // GZIP -> JettraBinary
                 try (DataInputStream in = new DataInputStream(new GZIPInputStream(bufIn))) {
                     return JettraBinarySerialization.deserialize(in);
                 }
             } else if (b1 == 0x00 && b2 == 0x00) {
                 // Custom Uncompressed JettraBinary
                 // Skip magic bytes
                 bufIn.read(); // 0x00
                 bufIn.read(); // 0x00
                 try (DataInputStream in = new DataInputStream(bufIn)) {
                     return JettraBinarySerialization.deserialize(in);
                 }
             } else {
                 // Fallback: Check for CBOR Map (Major type 5 [0xa0..0xbf] or Indefinite [0xbf])
                 if (b1 != -1 && (b1 == 0xbf || (b1 >= 0xa0 && b1 <= 0xbf))) {
                     // CBOR
                     return cborMapper.readValue(bufIn, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>(){});
                 } else {
                     // Fallback to JettraBinary uncompressed OLD format (no magic, just starts with type byte?)
                     // Type byte for Map is 8.
                     // But wait, old format had GZIP header 1f 8b OR just data?
                     // Previous implementation: "Fallback to JettraBinary uncompressed"
                     // This means it was possbile to have raw uncompressed data.
                     // Assuming it starts with valid Type Byte (e.g. 8).
                     try (DataInputStream in = new DataInputStream(bufIn)) {
                         return JettraBinarySerialization.deserialize(in);
                     }
                 }
             }
        }
    }

    @Override
    public String save(String database, String collection, Map<String, Object> document) throws Exception {
        lock.writeLock().lock();
        try {
            if (validator != null) {
                validator.validate(database, collection, document);
            }

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

            Path filePath = collectionDir.resolve(id + ".jdbbin"); // Use .jdbbin extension to distinguish? Or keep .jdb? User asked for verification. simpler to keep .jdb? 
            // The file content is different. If we use same extension, we can't easily tell file type without reading header. 
            // I will use .jdb still because existing code might rely on it (like backup/restore). 
            // Better: use .jdb but maybe add a magic header? 
            // For now, I'll stick to .jdb for compatibility with directory listing logic in existing tools if they look for .jdb.
            // Wait, existing tools use `mapper.readValue`. If I give them binary file, they will crash.
            // So this database MUST only be accessed by this engine.
            
            Path finalPath = collectionDir.resolve(id + ".jdb");

            if (Files.exists(finalPath)) {
                createVersion(database, collection, id);
            }
            
            writeMap(finalPath, document);

            // Update Cache
            cache.put(getCacheKey(database, collection, id), document);

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
            
            // Check cache first
            String cacheKey = getCacheKey(database, collection, id);
            if (cache.containsKey(cacheKey)) {
                return cache.get(cacheKey);
            }

            if (!Files.exists(filePath)) {
                return null;
            }
            Map<String, Object> doc = readMap(filePath);
            cache.put(cacheKey, doc);
            return doc;
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
                    if (filter == null || filter.isEmpty()) {
                        if (skipped < offset) {
                            skipped++;
                            continue;
                        }
                    }

                    Map<String, Object> docMap;
                    try {
                        String id = file.getFileName().toString().replace(".jdb", "");
                        String cacheKey = getCacheKey(database, collection, id);
                        if (cache.containsKey(cacheKey)) {
                            docMap = cache.get(cacheKey);
                        } else {
                            docMap = readMap(file);
                            cache.put(cacheKey, docMap);
                        }
                    } catch (Exception e) {
                        System.err.println("Skipping corrupted file (Engine): " + file.getFileName() + " - " + e.getMessage());
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
                Map<String, Object> document = findByID(database, collection, id);
                if (document != null) {
                     validator.validateDelete(database, collection, document);
                }
            }
            Path filePath = Paths.get(dataDirectory, database, collection, id + ".jdb");
            if (Files.exists(filePath)) {
                createVersion(database, collection, id);
                Files.delete(filePath);
                cache.remove(getCacheKey(database, collection, id));
            }
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

    @Override
    public void createDatabase(String name, String engine) throws Exception {
        lock.writeLock().lock();
        try {
            Path dbDir = Paths.get(dataDirectory, name);
            Files.createDirectories(dbDir);
        } finally {
            lock.writeLock().unlock();
        }
    }

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
    public void restoreDatabase(String zipFilename, String targetDatabase) throws Exception {
        lock.writeLock().lock();
        try {
            Path backupsDir = Paths.get("backups");
            Path zipPath = backupsDir.resolve(zipFilename);

            if (!Files.exists(zipPath)) {
                throw new Exception("Backup file " + zipFilename + " not found");
            }

            Path targetDir = Paths.get(dataDirectory, targetDatabase);
            
            if (Files.exists(targetDir)) {
                 try (Stream<Path> walk = Files.walk(targetDir)) {
                    walk.sorted(java.util.Comparator.reverseOrder())
                        .map(Path::toFile)
                        .forEach(File::delete);
                }
            }
            Files.createDirectories(targetDir);

            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(Files.newInputStream(zipPath))) {
                java.util.zip.ZipEntry zipEntry = zis.getNextEntry();
                while (zipEntry != null) {
                    Path newPath = targetDir.resolve(zipEntry.getName());
                    if (!newPath.normalize().startsWith(targetDir.normalize())) {
                         throw new Exception("Zip Entry outside of target directory");
                    }

                    if (zipEntry.isDirectory()) {
                        Files.createDirectories(newPath);
                    } else {
                        Files.createDirectories(newPath.getParent());
                        Files.copy(zis, newPath);
                    }
                    zipEntry = zis.getNextEntry();
                }
                zis.closeEntry();
            }

        } finally {
            lock.writeLock().unlock();
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
            try (Stream<Path> files = Files.list(txDir)) {
                List<Path> ops = files.sorted().toList();
                
                for (Path opFile : ops) {
                    Map<String, Object> opData = readMap(opFile);
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
    public void saveTx(String database, String collection, Map<String, Object> document, String txID) throws Exception {
        Path txDir = Paths.get(dataDirectory, "_system", "_transactions", txID);
        if (!Files.exists(txDir)) throw new Exception("Transaction " + txID + " not active");
        
        Map<String, Object> op = new HashMap<>();
        op.put("type", "save");
        op.put("db", database);
        op.put("col", collection);
        op.put("doc", document);
        
        String filename = System.nanoTime() + ".op";
        writeMap(txDir.resolve(filename), op);
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
        writeMap(txDir.resolve(filename), op);
    }


    private void createVersion(String database, String collection, String id) {
        try {
            Path original = Paths.get(dataDirectory, database, collection, id + ".jdb");
            if (!Files.exists(original)) return;
            
            Path versionDir = Paths.get(dataDirectory, database, collection, "_versions", id);
            Files.createDirectories(versionDir);
            
            String timestamp = String.valueOf(System.currentTimeMillis());
            
            Path versionPath = versionDir.resolve(timestamp + ".jdb");
            Files.copy(original, versionPath);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public List<String> getVersions(String database, String collection, String id) throws Exception {
        lock.readLock().lock();
        try {
            Path versionDir = Paths.get(dataDirectory, database, collection, "_versions", id);
            if (!Files.exists(versionDir)) {
                return new ArrayList<>();
            }
            try (Stream<Path> files = Files.list(versionDir)) {
                return files.map(p -> p.getFileName().toString().replace(".jdb", ""))
                        .sorted(java.util.Comparator.reverseOrder())
                        .toList();
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void restoreVersion(String database, String collection, String id, String version) throws Exception {
        lock.writeLock().lock();
        try {
            Path versionFile = Paths.get(dataDirectory, database, collection, "_versions", id, version + ".jdb");
            if (!Files.exists(versionFile)) {
                throw new Exception("Version " + version + " not found");
            }
            
            createVersion(database, collection, id);
            
            Path targetFile = Paths.get(dataDirectory, database, collection, id + ".jdb");
            Files.copy(versionFile, targetFile, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Map<String, Object> getVersionContent(String database, String collection, String id, String version) throws Exception {
        lock.readLock().lock();
        try {
            Path versionFile = Paths.get(dataDirectory, database, collection, "_versions", id, version + ".jdb");
            if (!Files.exists(versionFile)) {
                return null;
            }
            return readMap(versionFile);
        } finally {
            lock.readLock().unlock();
        }
    }


    @Override
    public String getDatabaseEngine(String database) throws Exception {
        return "JettraEngineStore";
    }
}
