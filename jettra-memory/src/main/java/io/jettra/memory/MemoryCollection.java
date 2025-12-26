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
    
    // The "MemTable": Concurrent sorted map for O(log n) operations
    // <Key, Value>
    private final ConcurrentSkipListMap<String, Object> activeMemTable; // Simply Object for value placeholder
    
    // In a real LSM, we switch active to immutable. 
    // For this prototype, we'll keep it simple but structure it for expansion.
    
    public MemoryCollection(String name, JettraMemoryDB db) {
        this.name = name;
        this.db = db;
        this.activeMemTable = new ConcurrentSkipListMap<>();
    }
    
    public void insert(String key, Object document, long txId) {
        // In MVCC, typically we wrap the document with version info
        activeMemTable.put(key, document);
    }
    
    public Object get(String key) {
        return activeMemTable.get(key);
    }
    
    public void delete(String key, long txId) {
        // tombstone in LSM
        activeMemTable.remove(key); 
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
