package io.jettra.core;

import java.util.logging.Logger;

import io.jettra.core.storage.BTreeIndexEngine;
import io.jettra.core.storage.DocumentStore;
import io.jettra.core.storage.IndexEngine;
import io.jettra.core.storage.RouterDocumentStore;
import io.jettra.core.validation.ValidationManager;

public class Engine {
    private static final Logger LOGGER = Logger.getLogger(Engine.class.getName());

    private final io.jettra.core.config.ConfigManager configManager;
    private final DocumentStore store;
    private final IndexEngine indexer;
    private final io.jettra.core.auth.AuthManager auth;
    private final io.jettra.core.raft.RaftNode raftNode;
    private final io.jettra.core.raft.RaftService raftService;

    public Engine(java.util.Map<String, Object> config, java.io.File configFile) throws Exception {
        this.configManager = new io.jettra.core.config.ConfigManager(configFile, config);

        String dataDir = (String) config.getOrDefault("DataDir", "data");

        this.store = new RouterDocumentStore(dataDir);
        ValidationManager validator = new ValidationManager(this.store);
        ((RouterDocumentStore) this.store).setValidator(validator);

        // Run Bootstrap (Must run before Auth/Raft which rely on system collections)
        io.jettra.core.bootstrap.BootstrapManager bootstrap = new io.jettra.core.bootstrap.BootstrapManager(store,
                config);
        bootstrap.init();

        this.indexer = new BTreeIndexEngine(dataDir);
        ((BTreeIndexEngine) this.indexer).loadIndexes();

        this.auth = new io.jettra.core.auth.AuthManager(store);

        // -- RAFT INIT --
        boolean distributed = (Boolean) config.getOrDefault("distributed", false);
        java.util.List<String> fedServersList = (java.util.List<String>) configManager.getOrDefault("FederatedServers", java.util.Collections.emptyList());
        boolean federated = !fedServersList.isEmpty();
        if (distributed || federated) {
            String nodeId = (String) config.getOrDefault("NodeID", "node-" + java.util.UUID.randomUUID().toString());
            @SuppressWarnings("unchecked")
            java.util.List<String> peers = (java.util.List<String>) config.getOrDefault("Peers",
                    new java.util.ArrayList<>());

            this.raftNode = new io.jettra.core.raft.RaftNode(nodeId, peers, configManager, this.store, this.indexer);
            this.raftService = new io.jettra.core.raft.RaftService(raftNode);
        } else {
            this.raftNode = null;
            this.raftService = null;
        }

        // Bootstrap moved up

        LOGGER.info("JettraDB Engine initialized with dataDir: " + dataDir);
    }

    public void start() {
        LOGGER.info("Starting JettraDB Engine...");
        if (this.raftNode != null) {
            this.raftNode.start();
        }
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

    public io.jettra.core.config.ConfigManager getConfigManager() {
        return configManager;
    }
}
