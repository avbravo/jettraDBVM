package io.jettra.memory;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * JettraMemoryDB - High performance in-memory database.
 * 
 * Features:
 * - Pure in-memory storage (no disk I/O for data ops).
 * - ACID Transactions (Snapshot isolation/MVCC).
 * - LSM-Tree inspired data structures for high write throughput.
 * - Resource monitoring and optimization.
 */
public class JettraMemoryDB {
    private static final Logger LOGGER = Logger.getLogger(JettraMemoryDB.class.getName());

    private final String name;
    private final MemoryConfig config;
    private final ResourceMonitor resourceMonitor;
    private final TransactionManager transactionManager;
    
    // Databases container: Map<DatabaseName, Map<CollectionName, MemoryCollection>>
    private final Map<String, Map<String, MemoryCollection>> databases;

    public JettraMemoryDB(String name, MemoryConfig config) {
        this.name = name;
        this.config = config;
        this.databases = new ConcurrentHashMap<>();
        
        this.resourceMonitor = new ResourceMonitor(config);
        this.transactionManager = new TransactionManager(this);
        
        initialize();
    }

    private void initialize() {
        LOGGER.info(() -> "Starting JettraMemoryDB: " + name);
        resourceMonitor.startMonitoring();
    }

    public Map<String, MemoryCollection> getDatabase(String dbName) {
        return databases.computeIfAbsent(dbName, k -> new ConcurrentHashMap<>());
    }

    public MemoryCollection createCollection(String dbName, String collectionName) {
        return getDatabase(dbName).computeIfAbsent(collectionName, k -> new MemoryCollection(k, this));
    }

    public MemoryCollection getCollection(String dbName, String collectionName) {
        Map<String, MemoryCollection> db = databases.get(dbName);
        return (db != null) ? db.get(collectionName) : null;
    }
    
    public int getCollectionCount() {
        return databases.values().stream().mapToInt(Map::size).sum();
    }
    
    public java.util.Set<String> getDatabaseNames() {
        return Collections.unmodifiableSet(databases.keySet());
    }

    public java.util.Set<String> getCollectionNames(String dbName) {
        Map<String, MemoryCollection> db = databases.get(dbName);
        return (db != null) ? Collections.unmodifiableSet(db.keySet()) : Collections.emptySet();
    }
    
    public TransactionManager getTransactionManager() {
        return transactionManager;
    }

    public ResourceMonitor getResourceMonitor() {
        return resourceMonitor;
    }

    public void shutdown() {
        LOGGER.info(() -> "Shutting down JettraMemoryDB: " + name);
        resourceMonitor.stopMonitoring();
    }
}
