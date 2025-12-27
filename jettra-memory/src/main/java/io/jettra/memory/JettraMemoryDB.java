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
        setupSystemDb();
    }

    private void setupSystemDb() {
        MemoryCollection users = createCollection("_system", "users");
        if (users.size() == 0) {
            Map<String, Object> admin = new java.util.HashMap<>();
            admin.put("username", "admin");
            admin.put("password", config.getAdminPassword());
            admin.put("description", "Built-in Administrator");
            admin.put("role", "admin");
            users.insert("admin", admin, 0);
        }
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

    public void deleteDatabase(String dbName) {
        databases.remove(dbName);
    }

    public void deleteCollection(String dbName, String collectionName) {
        Map<String, MemoryCollection> db = databases.get(dbName);
        if (db != null) {
            db.remove(collectionName);
        }
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
