package io.jettra.core.bootstrap;

import io.jettra.core.storage.DocumentStore;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

public class BootstrapManager {
    private final DocumentStore store;
    private final Map<String, Object> config;

    public BootstrapManager(DocumentStore store, Map<String, Object> config) {
        this.store = store;
        this.config = config;
    }

    public void init() {
        try {
            initUsers();
            initCluster();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initUsers() throws Exception {
        String db = "_system";
        String col = "_users";

        // Check if admin exists
        Map<String, Object> filter = new HashMap<>();
        filter.put("username", "admin");

        List<Map<String, Object>> users = store.query(db, col, filter, 1, 0);
        if (users.isEmpty()) {
            Map<String, Object> admin = new HashMap<>();
            admin.put("username", "admin");
            admin.put("password", "adminadmin");
            admin.put("role", "admin");
            store.save(db, col, admin);
            System.out.println("Created default admin user in _system._users");
        }
    }

    private void initCluster() throws Exception {
        String db = "_system";
        String col = "_cluster";

        // Check if cluster info exists
        int count = store.count(db, col);
        if (count == 0) {
            Map<String, Object> clusterInfo = new HashMap<>();
            String clusterName = (String) config.getOrDefault("cluster", "cluster-1");

            clusterInfo.put("idcluster", UUID.randomUUID().toString());
            clusterInfo.put("cluster", clusterName);
            clusterInfo.put("descripcion", "Default Cluster created on bootstrap");

            store.save(db, col, clusterInfo);
            System.out.println("Created default cluster info in _system._cluster");
        }
    }
}
