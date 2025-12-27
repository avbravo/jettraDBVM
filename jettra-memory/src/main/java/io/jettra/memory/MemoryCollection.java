package io.jettra.memory;

import java.util.concurrent.ConcurrentSkipListMap;
import java.util.logging.Logger;

/**
 * Represents a Collection in the in-memory database.
 * Uses an LSM-like structure adapted for memory:
 * - Active MemTable for writes.
 * - Immutable MemTables for read-only snapshots.
 * - Background compaction to merge tables and reclaim space.
 */
public class MemoryCollection {
    private static final Logger LOGGER = Logger.getLogger(MemoryCollection.class.getName());
    
    private final String name;
    private final JettraMemoryDB db;
    
    public record DocVersion(Object data, long timestamp, String versionId, String author) {}
    
    private final ConcurrentSkipListMap<String, Object> activeMemTable;
    private final java.util.Map<String, java.util.List<DocVersion>> versionHistory;
    private final java.util.List<java.util.Map<String, Object>> indexes;
    
    public MemoryCollection(String name, JettraMemoryDB db) {
        this.name = name;
        this.db = db;
        this.activeMemTable = new ConcurrentSkipListMap<>();
        this.versionHistory = new java.util.concurrent.ConcurrentHashMap<>();
        this.indexes = new java.util.concurrent.CopyOnWriteArrayList<>();
    }

    public void createIndex(java.util.Map<String, Object> index) {
        indexes.add(index);
    }

    public void deleteIndex(String field) {
        indexes.removeIf(idx -> field.equals(idx.get("field")));
    }

    public java.util.List<java.util.Map<String, Object>> getIndexes() {
        return java.util.Collections.unmodifiableList(indexes);
    }
    
    public void insert(String key, Object document, long txId) {
        activeMemTable.put(key, document);
        
        // Add to history
        java.util.List<DocVersion> history = versionHistory.computeIfAbsent(key, k -> new java.util.concurrent.CopyOnWriteArrayList<>());
        String vId = String.valueOf(System.currentTimeMillis());
        history.add(new DocVersion(document, System.currentTimeMillis(), vId, "admin"));
    }
    
    public Object get(String key) {
        return activeMemTable.get(key);
    }
    
    public java.util.List<DocVersion> getVersions(String key) {
        return versionHistory.getOrDefault(key, java.util.Collections.emptyList());
    }

    public DocVersion getVersion(String key, String versionId) {
        java.util.List<DocVersion> history = versionHistory.get(key);
        if (history != null) {
            for (DocVersion v : history) {
                if (v.versionId().equals(versionId)) return v;
            }
        }
        return null;
    }

    public void restoreVersion(String key, String versionId) {
        DocVersion v = getVersion(key, versionId);
        if (v != null) {
            insert(key, v.data(), 0);
        }
    }

    public void delete(String key, long txId) {
        activeMemTable.remove(key); 
        // We might want to keep history? jettra-server seems to keep it if requested by ID.
    }
    
    /**
     * Compacts memory by merging structures.
     * In an in-memory LSM, this consolidates scattered nodes and removes garbage.
     */
    public void optimize() {
        LOGGER.info("Optimizing collection: " + name);
        // Implementation would create a new dense structure and swap it in
    }

    public java.util.Map<String, Object> getAll() {
        return new java.util.HashMap<>(activeMemTable);
    }

    public String getName() {
        return name;
    }
    
    public int size() {
        return activeMemTable.size();
    }
}
