package io.jettra.test;

import java.util.Map;

import io.jettra.driver.JettraClient;

public class ClusterMonitor {
    public static void main(String[] args) {
        String host = args.length > 0 ? args[0] : "localhost";
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8080;
        
        System.out.println("Monitoring Cluster via Driver at " + host + ":" + port);
        JettraClient client = new JettraClient(host, port, "admin", "admin");
        
        while (true) {
            try {
                Map<String, Object> status = client.getClusterStatus();
                System.out.println(System.currentTimeMillis() + " | Leader: " + status.get("leaderId") + " | State: " + status.get("state") + " | Term: " + status.get("term"));
            } catch (Exception e) {
                System.out.println(System.currentTimeMillis() + " | Error: " + e.getMessage());
            }
            try { Thread.sleep(2000); } catch (InterruptedException e) {}
        }
    }
}
