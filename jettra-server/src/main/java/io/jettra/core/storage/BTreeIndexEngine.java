package io.jettra.core.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

public class BTreeIndexEngine implements IndexEngine {
    private final String dataDirectory;
    // CollectionKey (db.col) -> FieldKey ("field1,field2") -> ValueKey
    // ("val1,val2") -> ID
    private final Map<String, Map<String, Map<String, String>>> indexes = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper(new CBORFactory());
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    public BTreeIndexEngine(String dataDirectory) {
        this.dataDirectory = dataDirectory;
    }

    @Override
    public void reload() {
        loadIndexes();
    }

    public void loadIndexes() {
        // In a real implementation, we would scan all DBs/Cols and load _indexes.jdb
        // For simplicity/parity with Go prototype, we can lazily load or scan here.
        // Implementing basic scan:
        try {
            Path root = Paths.get(dataDirectory);
            if (!Files.exists(root))
                return;

            try (Stream<Path> dbs = Files.list(root)) {
                for (Path db : dbs.toList()) {
                    if (!Files.isDirectory(db))
                        continue;
                    try (Stream<Path> cols = Files.list(db)) {
                        for (Path col : cols.toList()) {
                            if (!Files.isDirectory(col))
                                continue;
                            loadCollectionIndexes(db.getFileName().toString(), col.getFileName().toString());
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void loadCollectionIndexes(String db, String col) {
        try {
            List<IndexDefinition> defs = getIndexes(db, col);
            if (defs.isEmpty())
                return;

            String key = db + "." + col;
            indexes.putIfAbsent(key, new ConcurrentHashMap<>());

            for (IndexDefinition def : defs) {
                String fieldKey = String.join(",", def.fields());
                indexes.get(key).putIfAbsent(fieldKey, new ConcurrentHashMap<>());
            }

            // Rebuild index from data (Naive implementation matching Go)
            Path colDir = Paths.get(dataDirectory, db, col);
            try (Stream<Path> files = Files.list(colDir)) {
                files.filter(p -> p.toString().endsWith(".jdb") && !p.endsWith("_indexes.jdb"))
                        .forEach(p -> {
                            try {
                                Map<String, Object> doc = mapper.readValue(p.toFile(),
                                        new TypeReference<Map<String, Object>>() {
                                        });
                                String id = (String) doc.get("_id");
                                if (id == null)
                                    id = (String) doc.get("id");

                                if (id != null) {
                                    updateIndex(db, col, id, doc);
                                }
                            } catch (Exception ignore) {
                            }
                        });
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void createIndex(String database, String collection, List<String> fields, boolean unique, boolean sequential)
            throws Exception {
        lock.writeLock().lock();
        try {
            List<IndexDefinition> current = getIndexes(database, collection);
            // Check if exists
            for (IndexDefinition def : current) {
                if (def.fields().equals(fields))
                    return; // Already exists
            }

            current.add(new IndexDefinition(fields, unique, sequential));
            saveIndexes(database, collection, current);

            // Update memory and rebuild
            String key = database + "." + collection;
            indexes.putIfAbsent(key, new ConcurrentHashMap<>());
            String fieldKey = String.join(",", fields);
            indexes.get(key).put(fieldKey, new ConcurrentHashMap<>());

            rebuildIndex(database, collection, fields);
        } finally {
            lock.writeLock().unlock();
        }
    }

    private void saveIndexes(String database, String collection, List<IndexDefinition> defs) throws Exception {
        Path validPath = Paths.get(dataDirectory, database, collection);
        Files.createDirectories(validPath);
        Path idxPath = validPath.resolve("_indexes.jdb");
        mapper.writeValue(idxPath.toFile(), defs);
    }

    private void rebuildIndex(String database, String collection, List<String> fields) {
        // Scan all docs and call updateIndex
        // For efficiency, we just call loadCollectionIndexes logic again or similar?
        // Let's just scan files.
        Path colDir = Paths.get(dataDirectory, database, collection);
        if (!Files.exists(colDir))
            return;

        try (Stream<Path> files = Files.list(colDir)) {
            files.filter(p -> p.toString().endsWith(".jdb") && !p.endsWith("_indexes.jdb"))
                    .forEach(p -> {
                        try {
                            Map<String, Object> doc = mapper.readValue(p.toFile(),
                                    new TypeReference<Map<String, Object>>() {
                                    });
                            String id = (String) doc.get("_id");
                            if (id == null)
                                id = (String) doc.get("id");

                            if (id != null) {
                                updateIndex(database, collection, id, doc);
                            }
                        } catch (Exception ignore) {
                        }
                    });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void updateIndex(String database, String collection, String id, Map<String, Object> document)
            throws Exception {
        // No lock needed for concurrent map if careful, but to be safe with structure
        // updates?
        // We assume structure (indexes map keys) is stable after creation.

        String key = database + "." + collection;
        Map<String, Map<String, String>> colIdx = indexes.get(key);
        if (colIdx == null)
            return; // No indexes for this col

        for (Map.Entry<String, Map<String, String>> entry : colIdx.entrySet()) {
            String fieldKey = entry.getKey();
            String[] fields = fieldKey.split(",");

            List<String> values = new ArrayList<>();
            boolean hasAll = true;
            for (String f : fields) {
                Object val = document.get(f);
                if (val == null) {
                    hasAll = false;
                    break;
                }
                values.add(val.toString());
            }

            if (hasAll) {
                String valKey = String.join(",", values);
                entry.getValue().put(valKey, id);
            }
        }
    }

    @Override
    public List<String> findWithIndex(String database, String collection, List<String> fields, List<String> values)
            throws Exception {
        String key = database + "." + collection;
        String fieldKey = String.join(",", fields);
        String valKey = String.join(",", values);

        Map<String, Map<String, String>> colIdx = indexes.get(key);
        if (colIdx != null) {
            Map<String, String> valMap = colIdx.get(fieldKey);
            if (valMap != null) {
                String id = valMap.get(valKey);
                if (id != null) {
                    return Collections.singletonList(id);
                }
            }
        }
        return new ArrayList<>();
    }

    @Override
    public List<IndexDefinition> getIndexes(String database, String collection) throws Exception {
        Path idxPath = Paths.get(dataDirectory, database, collection, "_indexes.jdb");
        if (!Files.exists(idxPath))
            return new ArrayList<>();

        return mapper.readValue(idxPath.toFile(), new TypeReference<List<IndexDefinition>>() {
        });
    }

    @Override
    public void deleteIndex(String database, String collection, List<String> fields) throws Exception {
        lock.writeLock().lock();
        try {
            List<IndexDefinition> current = getIndexes(database, collection);
            List<IndexDefinition> next = new ArrayList<>();
            String fieldKey = String.join(",", fields);

            boolean changed = false;
            for (IndexDefinition def : current) {
                if (!String.join(",", def.fields()).equals(fieldKey)) {
                    next.add(def);
                } else {
                    changed = true;
                }
            }

            if (changed) {
                saveIndexes(database, collection, next);

                String key = database + "." + collection;
                if (indexes.containsKey(key)) {
                    indexes.get(key).remove(fieldKey);
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }
}
