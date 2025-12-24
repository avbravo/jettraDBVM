package io.jettra.federated.shell;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class FederatedShell {
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private String baseUrl = "http://localhost:9000";
    private String token = null;

    public static void main(String[] args) {
        new FederatedShell().run();
    }

    public void run() {
        try {
            Terminal terminal = TerminalBuilder.builder()
                    .system(true)
                    .build();
            LineReader reader = LineReaderBuilder.builder()
                    .terminal(terminal)
                    .build();

            System.out.println("========================================");
            System.out.println("    Jettra Federated Shell v1.0");
            System.out.println("========================================");
            System.out.println("Type 'help' for commands.");

            while (true) {
                String prompt = "jettra-fed [" + baseUrl + "]> ";
                String line = reader.readLine(prompt);
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equalsIgnoreCase("exit") || line.equalsIgnoreCase("quit")) {
                    break;
                }

                handleCommand(line);
            }
        } catch (Exception e) {
            System.err.println("Fatal error: " + e.getMessage());
        }
    }

    private void handleCommand(String line) {
        String[] parts = line.split("\\s+");
        String cmd = parts[0].toLowerCase();

        switch (cmd) {
            case "help":
                showHelp();
                break;
            case "connect":
                handleConnect(parts);
                break;
            case "login":
                handleLogin(parts);
                break;
            case "status":
                handleStatus();
                break;
            case "stop":
                handleStop(parts);
                break;
            case "nodes":
                handleNodes();
                break;
            case "leader":
                handleLeader();
                break;
            default:
                System.out.println("Unknown command: " + cmd);
        }
    }

    private void showHelp() {
        System.out.println("Available commands:");
        System.out.println("  connect <url>    - Set federated server URL (default: http://localhost:9000)");
        System.out.println("  login <u? <p>    - Login to federated server");
        System.out.println("  status           - View federated cluster and raft status");
        System.out.println("  leader           - View current leaders (Federated and DB)");
        System.out.println("  nodes            - View managed DB nodes and their status");
        System.out.println("  stop <url|self>  - Stop a federated node (Requires login)");
        System.out.println("  help             - Show this help");
        System.out.println("  exit/quit        - Exit shell");
    }

    private void handleConnect(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: connect <url>");
            return;
        }
        baseUrl = parts[1];
        if (!baseUrl.startsWith("http")) baseUrl = "http://" + baseUrl;
        System.out.println("Target set to: " + baseUrl);
        token = null; // Clear token on new connection
    }

    private void handleLogin(String[] parts) {
        String user, pass;
        if (parts.length < 3) {
            System.out.print("Username: ");
            user = System.console().readLine();
            System.out.print("Password: ");
            pass = new String(System.console().readPassword());
        } else {
            user = parts[1];
            pass = parts[2];
        }

        try {
            Map<String, String> body = Map.of("username", user, "password", pass);
            String json = mapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/federated/login"))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> res = mapper.readValue(response.body(), Map.class);
                token = (String) res.get("token");
                System.out.println("Login successful.");
            } else {
                System.out.println("Login failed: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleStatus() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/federated/status"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> status = mapper.readValue(response.body(), Map.class);
                System.out.println("--- Federated Cluster Status ---");
                System.out.println("Self ID: " + status.get("raftSelfId"));
                System.out.println("Self URL: " + status.get("raftSelfUrl"));
                System.out.println("Raft State: " + status.get("raftState"));
                System.out.println("Raft Term: " + status.get("raftTerm"));
                System.out.println("Raft Leader ID: " + status.get("raftLeaderId"));
                
                System.out.println("\n--- Federated Peers ---");
                Map<String, String> peerStates = (Map<String, String>) status.get("raftPeerStates");
                Map<String, String> peerIds = (Map<String, String>) status.get("raftPeerIds");
                List<String> peers = (List<String>) status.get("raftPeers");

                if (peers != null) {
                    for (String peer : peers) {
                        String pid = peerIds.get(peer);
                        String ps = peerStates.get(peer);
                        if (peer.equals(status.get("raftSelfUrl"))) {
                            System.out.printf("* %-25s | ID: %-10s | Status: %s (SELF)\n", peer, status.get("raftSelfId"), status.get("raftState"));
                        } else {
                            System.out.printf("  %-25s | ID: %-10s | Status: %s\n", peer, pid != null ? pid : "Unknown", ps != null ? ps : "OFFLINE/PENDING");
                        }
                    }
                }
            } else {
                System.out.println("Error: " + response.statusCode() + " " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleLeader() {
         try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/federated/status"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> status = mapper.readValue(response.body(), Map.class);
                System.out.println("Federated Leader: " + status.get("raftLeaderId"));
                System.out.println("Database Leader:  " + status.get("leaderId"));
            } else {
                 System.out.println("Error fetching status.");
            }
         } catch (Exception e) {
             System.out.println("Error: " + e.getMessage());
         }
    }

    private void handleNodes() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + "/federated/status"))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                Map<String, Object> status = mapper.readValue(response.body(), Map.class);
                List<Map<String, Object>> nodes = (List<Map<String, Object>>) status.get("nodes");
                String dbLeader = (String) status.get("leaderId");

                System.out.println("--- Managed DB Nodes ---");
                if (nodes == null || nodes.isEmpty()) {
                    System.out.println("No nodes registered.");
                } else {
                    System.out.printf("%-15s | %-25s | %-10s | %s\n", "Node ID", "URL", "Status", "Role");
                    System.out.println("----------------------------------------------------------------------");
                    for (Map<String, Object> node : nodes) {
                        String nid = (String) node.get("id");
                        String url = (String) node.get("url");
                        String st = (String) node.get("status");
                        String role = (nid != null && nid.equals(dbLeader)) ? "LEADER" : "FOLLOWER";
                        System.out.printf("%-15s | %-25s | %-10s | %s\n", nid, url, st, role);
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleStop(String[] parts) {
        if (parts.length < 2) {
            System.out.println("Usage: stop <url|self>");
            return;
        }

        if (token == null) {
            System.out.println("Error: You must login first.");
            return;
        }
        
        String target = parts[1];
        String stopUrl = baseUrl + "/federated/stop";
        if (!target.equalsIgnoreCase("self")) {
            if (!target.startsWith("http")) target = "http://" + target;
            stopUrl = target + "/federated/stop";
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(stopUrl))
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                System.out.println("Stop command sent successfully to " + target);
            } else {
                System.out.println("Error: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }
}
