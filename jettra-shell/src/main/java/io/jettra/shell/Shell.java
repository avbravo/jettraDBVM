package io.jettra.shell;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.Scanner;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Shell {

    private static HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String baseUrl = "http://localhost:8080";
    private static String token = null;
    private static String currentDb = null;

    public static void main(String[] args) {
        System.out.println("""
               _      _   _            ____  ______      ____  __
              | |    | | | |          |  _ \\|  _ \\ \\    / /  \\/  |
              | | ___| |_| |_ _ __ __ | | | | |_) \\ \\  / /| \\  / |
          _   | |/ _ \\ __| __| '__/ _` | | | |  _ < \\ \\/ / | |\\/| |
         | |__| |  __/ |_| |_| | | (_| | |_| | |_) | \\  /  | |  | |
          \\____/ \\___|\\__|\\__|_|  \\__,_|____/|____/   \\/   |_|  |_|
                                                                   
        JettraDBVM Shell v1.0
        """);
        System.out.println("Type 'help' for commands.");

        Scanner scanner = new Scanner(System.in);

        while (true) {
            String prompt = "jettra";
            if (currentDb != null) prompt += ":" + currentDb;
            prompt += "> ";
            
            System.out.print(prompt);
            String line = scanner.nextLine().trim();
            if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1] : "";

            try {
                switch (cmd) {
                    case "exit":
                    case "quit":
                        System.exit(0);
                        break;
                    case "help":
                        printHelp();
                        break;
                    case "connect":
                        baseUrl = arg.isEmpty() ? "http://localhost:8080" : arg;
                        System.out.println("Connected to " + baseUrl);
                        break;
                    case "login":
                        handleLogin(arg);
                        break;
                    case "use":
                        currentDb = arg;
                        System.out.println("Switched to db " + currentDb);
                        break;
                    case "show":
                        handleShow(arg);
                        break;
                    case "create":
                        handleCreate(arg);
                        break;
                    case "insert":
                        if (arg.trim().toLowerCase().startsWith("into ")) {
                            handleRawCommand(line);
                        } else {
                            handleInsert(arg);
                        }
                        break;
                    case "find":
                        if (arg.trim().toLowerCase().startsWith("in ")) {
                            handleRawCommand(line);
                        } else {
                            handleFind(arg);
                        }
                        break;
                    case "cls":
                    case "clear":
                        System.out.print("\033[H\033[2J");
                        System.out.flush();
                        break;

                    default:
                        // Treat as raw command (JQL/SQL/Mongo)
                        // Use original 'line' to preserve casing of arguments, but 'cmd' is lowercase.
                        // We should reconstruct properly or just pass 'line'.
                        handleRawCommand(line);
                }
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
            }
        }
    }

    private static void handleRawCommand(String cmd) throws Exception {
        if (token == null) { System.out.println("Not logged in."); return; }
        if (currentDb == null && !cmd.startsWith("create db") && !cmd.startsWith("show dbs")) { 
             // Some commands might not need db context, but most do.
             // Let's pass null and let server decide or error.
        }

        String json = mapper.writeValueAsString(Map.of("command", cmd));
        String uri = baseUrl + "/api/command";
        if (currentDb != null) uri += "?db=" + currentDb;

        HttpRequest req = HttpRequest.newBuilder()
           .uri(URI.create(uri))
           .header("Authorization", token)
           .header("Content-Type", "application/json")
           .POST(HttpRequest.BodyPublishers.ofString(json))
           .build();
        
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            // Pretty print JSON response if possible
            try {
                Object val = mapper.readValue(res.body(), Object.class);
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(val));
            } catch (Exception e) {
                System.out.println(res.body());
            }
        } else {
            System.out.println("Error (" + res.statusCode() + "): " + res.body());
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  connect <url>          Connect to server (default http://localhost:8080)");
        System.out.println("  login <user> <pass>    Login to get token");
        System.out.println("  use <db>               Select database");
        System.out.println("  show dbs               List databases");
        System.out.println("  show collections       List collections in current db");
        System.out.println("  create db <name>       Create database");
        System.out.println("  create col <name>      Create collection in current db");
        System.out.println("  insert <col> <json>    Insert document");
        System.out.println("  find <col>             Find all documents");
        System.out.println("  exit                   Exit shell");
    }

    private static void handleLogin(String arg) throws Exception {
        String[] creds = arg.split("\\s+");
        if (creds.length != 2) {
            System.out.println("Usage: login <username> <password>");
            return;
        }
        String json = mapper.writeValueAsString(Map.of("username", creds[0], "password", creds[1]));
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            Map<String, Object> map = mapper.readValue(res.body(), Map.class);
            token = (String) map.get("token");
            System.out.println("Logged in successfully.");
        } else {
            System.out.println("Login failed: " + res.body());
        }
    }

    private static void handleConnect(String url) {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        baseUrl = url;
        // Reset client
        client = HttpClient.newHttpClient();
        System.out.println("Connected to " + baseUrl);
    }

    private static void handleShow(String arg) throws Exception {
        if (token == null) {
            System.out.println("Not logged in.");
            return;
        }
        if (arg.equals("dbs")) {
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/dbs"))
                .header("Authorization", token)
                .GET()
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             System.out.println(res.body());
        } else if (arg.equals("collections")) {
            if (currentDb == null) {
                System.out.println("No database selected. use <db>");
                return;
            }
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/dbs/" + currentDb + "/cols"))
                .header("Authorization", token)
                .GET()
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             System.out.println(res.body());
        }
    }

    private static void handleCreate(String arg) throws Exception {
        if (token == null) { System.out.println("Not logged in."); return; }
        String[] parts = arg.split("\\s+", 2);
        if (parts.length < 2) return;

        if (parts[0].equals("db") || parts[0].equals("database")) {
             String json = mapper.writeValueAsString(Map.of("name", parts[1]));
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/dbs"))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             System.out.println(res.statusCode() == 200 ? "Created" : "Failed: " + res.body());
        } else if (parts[0].equals("col") || parts[0].equals("collection")) {
            if (currentDb == null) { System.out.println("No DB selected"); return; }
             String json = mapper.writeValueAsString(Map.of("database", currentDb, "collection", parts[1]));
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/cols"))
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             System.out.println(res.statusCode() == 200 ? "Created" : "Failed: " + res.body());
        }
    }

    private static void handleInsert(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected"); return; }
         
         String[] parts = arg.split("\\s+", 2);
         if (parts.length < 2) { System.out.println("Usage: insert <col> <json>"); return; }

         String col = parts[0];
         String docJson = parts[1];

         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/doc?db=" + currentDb + "&col=" + col))
            .header("Authorization", token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(docJson))
            .build();
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         System.out.println(res.statusCode() == 200 ? "Inserted" : "Failed: " + res.body());
    }

    private static void handleFind(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected"); return; }
         
         String col = arg;
         // defaults to limit 10
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/query?db=" + currentDb + "&col=" + col + "&limit=20"))
            .header("Authorization", token)
            .GET()
            .build();
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         System.out.println(res.body());
    }
}
