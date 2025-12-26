package io.jettra.memory.driver;

import java.util.logging.Logger;

import io.jettra.memory.JettraMemoryDB;
import io.jettra.memory.MemoryConfig;

/**
 * Driver for interacting with JettraMemoryDB.
 */
public class JettraMemoryDriver {
    private static final Logger LOGGER = Logger.getLogger(JettraMemoryDriver.class.getName());
    private JettraMemoryDB db;

    public JettraMemoryDriver() {
    }

    public synchronized void connect(String dbName) {
        if (db != null) {
            LOGGER.warning(() -> "Already connected to " + dbName);
            return;
        }
        // In a real scenario, this might connect via IPC or network if not embedded.
        // For now, assuming embedded usage or singleton accessor.
        MemoryConfig config = new MemoryConfig();
        db = new JettraMemoryDB(dbName, config);
        LOGGER.info(() -> "Connected to JettraMemoryDB: " + dbName);
    }
    
    public JettraMemoryDB getDB() {
        return db;
    }

    public synchronized void close() {
        if (db != null) {
            db.shutdown();
            db = null;
        }
    }
}
