package io.jettra.memory;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Configuration for JettraMemoryDB.
 * Defines resource limits and optimization flags.
 */
public class MemoryConfig {
    private static final Logger LOGGER = Logger.getLogger(MemoryConfig.class.getName());
    
    // Server Config
    private int port = 9090;
    private String host = "0.0.0.0";
    
    // Security Config
    private String adminPassword = "adminadmin";

    // Max memory usage in bytes (0 = unlimited/JVM limit)
    private long maxMemoryBytes = 0;
    
    // Threshold to trigger detailed GC or compaction
    private double criticalMemoryThreshold = 0.85;
    
    // LSM-Tree specific settings
    private int memTableSize = 1024 * 1024; // 1MB default segment size
    
    // Optimization flags
    private boolean useCompression = false; // Compress data in ram to save space (cpu tradeoff)
    
    // Federated Config
    private java.util.List<String> federatedServers = new java.util.ArrayList<>();

    public static MemoryConfig defaultConfig() {
        return new MemoryConfig();
    }
    
    public static MemoryConfig loadFromFile(String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        MemoryConfig config = new MemoryConfig();
        File file = new File(filePath);
        if (!file.exists()) {
            LOGGER.info(() -> "Config file not found: " + filePath + ". Using defaults.");
            return config;
        }
        
        try {
            JsonNode root = mapper.readTree(file);
            
            // Support root-level keys (similar to config.json)
            if (root.has("Port")) config.port = root.get("Port").asInt();
            if (root.has("Host")) config.host = root.get("Host").asText();
            if (root.has("AdminPassword")) config.adminPassword = root.get("AdminPassword").asText();
            if (root.has("MaxMemoryBytes")) config.maxMemoryBytes = root.get("MaxMemoryBytes").asLong();
            if (root.has("CriticalThreshold")) config.criticalMemoryThreshold = root.get("CriticalThreshold").asDouble();
            if (root.has("MemTableSize")) config.memTableSize = root.get("MemTableSize").asInt();
            if (root.has("UseCompression")) config.useCompression = root.get("UseCompression").asBoolean();
            if (root.has("FederatedServers")) {
                JsonNode serversNode = root.get("FederatedServers");
                if (serversNode.isArray()) {
                    config.federatedServers.clear();
                    for (JsonNode sn : serversNode) {
                        config.federatedServers.add(sn.asText());
                    }
                }
            }

            // Legacy nested structure
            if (root.has("server")) {
                JsonNode server = root.get("server");
                if (server.has("port")) config.port = server.get("port").asInt();
                if (server.has("host")) config.host = server.get("host").asText();
            }
            if (root.has("security")) {
                JsonNode security = root.get("security");
                if (security.has("admin_password")) config.adminPassword = security.get("admin_password").asText();
            }
            if (root.has("memory")) {
                JsonNode memory = root.get("memory");
                if (memory.has("max_memory_bytes")) config.maxMemoryBytes = memory.get("max_memory_bytes").asLong();
                if (memory.has("critical_threshold")) config.criticalMemoryThreshold = memory.get("critical_threshold").asDouble();
                if (memory.has("mem_table_size")) config.memTableSize = memory.get("mem_table_size").asInt();
                if (memory.has("use_compression")) config.useCompression = memory.get("use_compression").asBoolean();
            }
            if (root.has("federated")) {
                JsonNode federated = root.get("federated");
                if (federated.has("servers")) {
                    JsonNode serversNode = federated.get("servers");
                    if (serversNode.isArray() && config.federatedServers.isEmpty()) {
                        for (JsonNode sn : serversNode) {
                            config.federatedServers.add(sn.asText());
                        }
                    }
                }
            }
        } catch (IOException e) {
            LOGGER.warning(() -> "Failed to load config file: " + e.getMessage());
        }
        return config;
    }

    public void saveToFile(String filePath) {
        ObjectMapper mapper = new ObjectMapper();
        try {
            java.util.Map<String, Object> data = new java.util.LinkedHashMap<>();
            data.put("Host", host);
            data.put("Port", port);
            data.put("AdminPassword", adminPassword);
            data.put("MaxMemoryBytes", maxMemoryBytes);
            data.put("CriticalThreshold", criticalMemoryThreshold);
            data.put("MemTableSize", memTableSize);
            data.put("UseCompression", useCompression);
            data.put("FederatedServers", federatedServers);
            
            mapper.writerWithDefaultPrettyPrinter().writeValue(new File(filePath), data);
            LOGGER.info(() -> "Configuration saved to " + filePath);
        } catch (IOException e) {
            LOGGER.warning(() -> "Failed to save config file: " + e.getMessage());
        }
    }

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public double getCriticalMemoryThreshold() {
        return criticalMemoryThreshold;
    }

    public void setCriticalMemoryThreshold(double criticalMemoryThreshold) {
        this.criticalMemoryThreshold = criticalMemoryThreshold;
    }

    public int getMemTableSize() {
        return memTableSize;
    }

    public void setMemTableSize(int memTableSize) {
        this.memTableSize = memTableSize;
    }

    public boolean isUseCompression() {
        return useCompression;
    }

    public void setUseCompression(boolean useCompression) {
        this.useCompression = useCompression;
    }

    public java.util.List<String> getFederatedServers() {
        return federatedServers;
    }

    public void setFederatedServers(java.util.List<String> federatedServers) {
        this.federatedServers = federatedServers;
    }

    public int getPort() { return port; }
    public void setPort(int port) { this.port = port; }
    public String getHost() { return host; }
    public void setHost(String host) { this.host = host; }
    public String getAdminPassword() { return adminPassword; }
    public void setAdminPassword(String pass) { this.adminPassword = pass; }
}
