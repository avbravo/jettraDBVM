package io.jettra.core.config;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

public class ConfigManager {
    private static final Logger LOGGER = Logger.getLogger(ConfigManager.class.getName());
    private final ObjectMapper mapper = new ObjectMapper();
    private final File configFile;
    private final Map<String, Object> config;

    public ConfigManager(File configFile, Map<String, Object> initialConfig) {
        this.configFile = configFile;
        this.config = initialConfig;
    }

    public synchronized void save() {
        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(configFile, config);
            LOGGER.info("Configuration saved to " + configFile.getAbsolutePath());
        } catch (IOException e) {
            LOGGER.severe("Failed to save configuration: " + e.getMessage());
        }
    }

    public synchronized Object get(String key) {
        return config.get(key);
    }
    
    public synchronized Object getOrDefault(String key, Object defaultValue) {
         return config.getOrDefault(key, defaultValue);
    }

    public synchronized void set(String key, Object value) {
        config.put(key, value);
        save();
    }

    @SuppressWarnings("unchecked")
    public synchronized List<String> getPeers() {
        return (List<String>) config.getOrDefault("Peers", new ArrayList<>());
    }

    public synchronized void setPeers(List<String> peers) {
        config.put("Peers", new ArrayList<>(peers));
        save();
    }

    public synchronized void addPeer(String peerUrl) {
        List<String> peers = getPeers();
        if (!peers.contains(peerUrl)) {
            peers.add(peerUrl);
            setPeers(peers);
        }
    }

    public synchronized void removePeer(String peerUrl) {
        List<String> peers = getPeers();
        if (peers.remove(peerUrl)) {
            setPeers(peers);
        }
    }

    public synchronized boolean isBootstrap() {
        return (Boolean) config.getOrDefault("Bootstrap", false);
    }

    public synchronized void setBootstrap(boolean bootstrap) {
        if (isBootstrap() != bootstrap) {
            config.put("Bootstrap", bootstrap);
            save();
        }
    }
    
    public Map<String, Object> getConfigMap() {
        return config;
    }
}
