package io.jettra.test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.jettra.driver.JettraClient;

public class DriverTestMain {
    public static void main(String[] args) {
        System.out.println("=== Starting JettraDB Driver Test ===");
        
        try {
            // Initialize Client (Connecting to Node 1)
            JettraClient client = new JettraClient("localhost", 8080, "admin", "admin");
            
            // 1. Create Database
            System.out.println("Creating database 'testdb'...");
            client.createDatabase("testdb");
            
            // 2. Create Collection
            System.out.println("Creating collection 'items'...");
            client.createCollection("testdb", "items");
            
            // 3. Save Document
            System.out.println("Saving document...");
            Map<String, Object> doc = new HashMap<>();
            doc.put("name", "Product A");
            doc.put("price", 99.99);
            doc.put("tags", List.of("electronics", "new"));
            
            String id = client.saveDocument("testdb", "items", doc);
            System.out.println("Saved document with ID: " + id);
            
            // 4. Query Documents
            System.out.println("Querying documents...");
            List<Map<String, Object>> results = client.query("testdb", "items", 10, 0);
            System.out.println("Query results count: " + results.size());
            for (Map<String, Object> r : results) {
                System.out.println(" - " + r);
            }
            
            // 5. Cluster Status
            System.out.println("Fetching cluster status...");
            Map<String, Object> status = client.getClusterStatus();
            System.out.println("Cluster Status: " + status);
            
            System.out.println("\n=== Driver Test Completed Successfully ===");
            
        } catch (Exception e) {
            System.err.println("!!! Driver Test Failed !!!");
            e.printStackTrace();
            System.exit(1);
        }
    }
}
