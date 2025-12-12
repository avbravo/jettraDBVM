package io.jettra.core;

import io.helidon.config.Config;
import io.jettra.core.storage.BTreeIndexEngine;
import io.jettra.core.storage.DocumentStore;
import io.jettra.core.storage.FilePersistence;
import io.jettra.core.storage.IndexEngine;

import java.util.logging.Logger;

public class Engine {
    private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());

    private final java.util.Map<String, Object> config;
    private final DocumentStore store;
    private final io.jettra.core.auth.AuthManager auth;
    private final IndexEngine indexer;

    public Engine(java.util.Map<String, Object> config) throws Exception {
        this.config = config;
        String dataDir = (String) config.getOrDefault("DataDir", "data");

        this.store = new FilePersistence(dataDir);
        this.indexer = new BTreeIndexEngine(dataDir);
        ((BTreeIndexEngine) this.indexer).loadIndexes();

        this.auth = new io.jettra.core.auth.AuthManager(store);

        // Run Bootstrap
        io.jettra.core.bootstrap.BootstrapManager bootstrap = new io.jettra.core.bootstrap.BootstrapManager(store,
                config);
        bootstrap.init();

        LOGGER.info("JettraDB Engine initialized with dataDir: " + dataDir);
    }

    public void start() {
        LOGGER.info("Starting JettraDB Engine...");
        // Initialize other components here (Raft, API, etc.)
    }

    public DocumentStore getStore() {
        return store;
    }

    public IndexEngine getIndexer() {
        return indexer;
    }

    public io.jettra.core.auth.AuthManager getAuth() {
        return auth;
    }
}
