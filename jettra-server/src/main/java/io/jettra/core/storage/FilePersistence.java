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

public class FilePersistence implements DocumentStore {
    private final String dataDirectory;
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    // Use CBOR Factory for binary storage
    private final ObjectMapper mapper = new ObjectMapper(new CBORFactory());

    public FilePersistence(String dataDirectory) throws Exception {
        this.dataDirectory = dataDirectory;
        Files.createDirectories(Paths.get(dataDirectory));
    }

    @Override
    public String save(String database, String collection, Map<String, Object> document) throws Exception {
        lock.writeLock().lock();
        try {
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
                List<Path> files = paths.filter(p -> p.toString().endsWith(".jdb")).toList();

                int skipped = 0;
                for (Path file : files) {
                    // Optimization: if no filter, skip reading file content for offset
                    if (filter == null || filter.isEmpty()) {
                        if (skipped < offset) {
                            skipped++;
                            continue;
                        }
                    }

                    Map<String, Object> docMap = mapper.readValue(file.toFile(),
                            new TypeReference<Map<String, Object>>() {
                            });

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
        return "tx-0";
    }

    @Override
    public void commitTransaction(String txID) throws Exception {
    }

    @Override
    public void rollbackTransaction(String txID) throws Exception {
    }

    @Override
    public void saveTx(String database, String collection, Object document, String txID) throws Exception {
    }

    @Override
    public void deleteTx(String database, String collection, String id, String txID) throws Exception {
    }

}
