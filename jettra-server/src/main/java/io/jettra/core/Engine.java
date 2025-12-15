package io.jettra.core;

import java.util.logging.Logger;

import io.jettra.core.storage.BTreeIndexEngine;
import io.jettra.core.storage.DocumentStore;
import io.jettra.core.storage.FilePersistence;
import io.jettra.core.storage.IndexEngine;
import io.jettra.core.validation.ValidationManager;

public class Engine {
    private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());

    private final java.util.Map<String, Object> config;
    private final DocumentStore store;
    private final io.jettra.core.auth.AuthManager auth;
    private final IndexEngine indexer;
    private final io.jettra.core.raft.RaftNode raftNode;
    private final io.jettra.core.raft.RaftService raftService;

    public Engine(java.util.Map<String, Object> config) throws Exception {
        this.config = config;
        String dataDir = (String) config.getOrDefault("DataDir", "data");

        this.store = new FilePersistence(dataDir);
        ValidationManager validator = new ValidationManager(this.store);
        ((FilePersistence) this.store).setValidator(validator);

        this.indexer = new BTreeIndexEngine(dataDir);
        ((BTreeIndexEngine) this.indexer).loadIndexes();

        this.auth = new io.jettra.core.auth.AuthManager(store);

        // -- RAFT INIT --
        String nodeId = (String) config.getOrDefault("NodeID", "node-" + java.util.UUID.randomUUID().toString());

        
        io.jettra.core.raft.RaftLog raftLog = new io.jettra.core.raft.RaftLog(store);
        boolean isBootstrap = (Boolean) config.getOrDefault("Bootstrap", false);
        this.raftNode = new io.jettra.core.raft.RaftNode(nodeId, raftLog, store, isBootstrap);
        this.raftService = new io.jettra.core.raft.RaftService(raftNode);

        // Run Bootstrap
        io.jettra.core.bootstrap.BootstrapManager bootstrap = new io.jettra.core.bootstrap.BootstrapManager(store,
                config);
        bootstrap.init();

        LOGGER.info("JettraDB Engine initialized with dataDir: " + dataDir);
    }

    public void start() {
        LOGGER.info("Starting JettraDB Engine...");
        this.raftNode.start();
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
    
    public io.jettra.core.raft.RaftNode getRaftNode() {
        return raftNode;
    }
    
    public io.jettra.core.raft.RaftService getRaftService() {
        return raftService;
    }
}
