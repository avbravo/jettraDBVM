package io.jettra.shell;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;

import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.AggregateCompleter;
import org.jline.reader.impl.completer.ArgumentCompleter;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

public class Shell {

    private static HttpClient client = HttpClient.newHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();
    private static String baseUrl = "http://localhost:8080";
    private static String token = null;
    private static String currentDb = null;

    private static String currentTx = null;
    private static LineReader reader = null;
    private static boolean federatedMode = false;

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



        try {
            Terminal terminal = TerminalBuilder.builder()
                .system(true)
                .build();

            // Completers
            Completer commandCompleter = new StringsCompleter(
                "connect", "login", "use", "show", "create", "insert", 
                "find", "count", "delete", "backup", "restore", "export", "import", 
                "history", "revert", "federated", "exit", "quit", "help", "clear", "cls", 
                "begin", "commit", "rollback"
            );
            
            Completer showCompleter = new ArgumentCompleter(
                new StringsCompleter("show"),
                new StringsCompleter("dbs", "collections", "version")
            );

            Completer createCompleter = new ArgumentCompleter(
                new StringsCompleter("create"),
                new StringsCompleter("db", "col", "user")
            );
            
             Completer connectCompleter = new ArgumentCompleter(
                new StringsCompleter("connect"),
                new StringsCompleter("node", "federated")
            );

             Completer aggCompleter = new AggregateCompleter(
                showCompleter,
                createCompleter,
                connectCompleter,
                commandCompleter 
            );

            reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(aggCompleter)
                .variable(LineReader.HISTORY_FILE, System.getProperty("user.home") + "/.jettra_history")
                .build();

            while (true) {
                String prompt = "jettra";
                if (currentDb != null) prompt += ":" + currentDb;
                prompt += "> ";
                if (currentTx != null) prompt += "(TX:" + currentTx.substring(0,4) + ") ";

                String line = null;
                try {
                    line = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    System.out.println("Goodbye!");
                    return;
                }
                
                line = line.trim();
                if (line.isEmpty()) continue;

            String[] parts = line.split("\\s+", 2);
            String cmd = parts[0].toLowerCase();
            String arg = parts.length > 1 ? parts[1] : "";

            if (federatedMode && !cmd.equals("federated") && !cmd.equals("connect") && !cmd.equals("help") && 
                !cmd.equals("login") && !cmd.equals("exit") && !cmd.equals("quit") && !cmd.equals("cls") && !cmd.equals("clear")) {
                System.out.println("Command not available in Federated Mode. Only 'federated' commands are allowed.");
                continue;
            }

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
                        handleConnectCommand(arg);
                        break;
                    case "login":
                        handleLogin(arg);
                        break;
                    case "use":
                        currentDb = arg;
                        System.out.println("Switched to db " + currentDb);
                        break;
                    case "show":
                        if (arg.trim().startsWith("version ")) {
                             handleShowVersion(arg.substring(8));
                        } else {
                             handleShow(arg);
                        }
                        break;
                    case "create":
                        String createArg = arg.trim();
                        if (createArg.toLowerCase().startsWith("user")) {
                            handleCreateUser(createArg);
                        } else {
                            handleCreate(createArg);
                        }
                        break;
                    case "insert":
                        if (arg.trim().toLowerCase().startsWith("into ")) {
                            handleRawCommand(line);
                        } else {
                            handleInsert(arg);
                        }
                        break;
                    case "count":
                        handleCount(arg);
                        break;
                    case "find":
                        if (arg.trim().toLowerCase().startsWith("in ")) {
                            handleRawCommand(line);
                        } else {
                            handleFind(arg);
                        }
                        break;
                    case "delete":
                        handleDelete(arg);
                        break;
                    case "begin":
                        handleBeginTx();
                        break;
                    case "commit":
                        handleCommitTx();
                        break;
                    case "rollback":
                        handleRollbackTx();
                        break;

                    case "backup":
                        handleBackup(arg);
                        break;
                    case "restore":
                        handleRestore(arg);
                        break;
                    case "export":
                        handleExport(arg);
                        break;
                    case "import":
                        handleImport(arg);
                        break;
                    case "cls":
                    case "clear":
                        System.out.print("\033[H\033[2J");
                        System.out.flush();
                        break;
                    case "history":
                        handleHistory(arg);
                        break;
                    case "revert":
                        handleRevert(arg);
                        break;
                    case "cluster":
                        handleCluster(arg);
                        break;
                    case "federated":
                        handleFederated(arg);
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void handleShowVersion(String arg) throws Exception {
        if (currentDb == null) { System.out.println("No db selected."); return; }
        
        String[] parts = arg.trim().split("\\s+");
        if (parts.length < 3) {
            System.out.println("Usage: show version <col> <id> <version>");
            return;
        }
        
        String col = parts[0];
        String id = parts[1];
        String version = parts[2];
        
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/version?db=" + currentDb + "&col=" + col + "&id=" + id + "&version=" + version))
            .header("Authorization", token != null ? token : "")
            .GET()
            .build();
            
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            try {
                Object val = mapper.readValue(res.body(), Object.class);
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(val));
            } catch (Exception e) {
                System.out.println(res.body());
            }
        } else {
             System.out.println("Error: " + res.body());
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
                if (val instanceof java.util.List) {
                    paginateList((java.util.List<?>) val);
                } else {
                    System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(val));
                }
            } catch (Exception e) {
                System.out.println(res.body());
            }
        } else {
            System.out.println("Error (" + res.statusCode() + "): " + res.body());
        }
    }

    private static void printHelp() {
        System.out.println("Commands:");
        System.out.println("  connect node <url>      Connect to a database node (default http://localhost:8080)");
        System.out.println("  connect federated <url> Connect to a federated server");
        
        if (!federatedMode) {
            System.out.println("  login <user> <pass>    Login to get token");
            System.out.println("  use <db>               Select database");
            System.out.println("  show dbs               List databases");
            System.out.println("  show collections       List collections in current db");
            System.out.println("  create user <name>     Create new user (interactive)");
            System.out.println("  create db <name>       Create database");
            System.out.println("  create col <name>      Create collection in current db");
            System.out.println("  insert <col> <json>    Insert document");
            System.out.println("  find <col>             Find all documents (paginated)");
            System.out.println("  backup [db]            Create backup of current or specified db");
            System.out.println("  restore <file> <db>    Restore database from zip file");
            System.out.println("  export <col> <fmt> <file> Export collection to file (fmt: json/csv)");
            System.out.println("  import <col> <fmt> <file> Import collection from file");
            System.out.println("  history <col> <id>     Show version history of a document");
            System.out.println("  show version <col> <id> <ver> Show content of a specific version");
            System.out.println("  revert <col> <id> <ver> Revert document to a specific version");
        }

        System.out.println("  federated show         List federated servers and cluster status");
        System.out.println("  federated leader       Show federated leader info");
        System.out.println("  federated nodes        Show DB nodes and DB leader");
        System.out.println("  federated node-leader  Show DB leader info");
        System.out.println("  federated add <url>    Add a federated server to the cluster");
        System.out.println("  federated stop <url>   Stop a federated server");
        System.out.println("  federated remove <url> Remove a federated server from the cluster");
        System.out.println("  cls / clear            Clear screen");
        System.out.println("  help                   Show this help");
        System.out.println("  exit                   Exit shell");
    }



    private static void handleHistory(String arg) throws Exception {
        if (currentDb == null && arg.split("\\s+").length < 3) { System.out.println("No db selected. Usage: history <col> <id>"); return; }
        
        String[] parts = arg.split("\\s+");
        if (parts.length < 2) {
            System.out.println("Usage: history <col> <id>");
            return;
        }
        String col = parts[0];
        String id = parts[1];
        
        String url = baseUrl + "/api/versions?db=" + currentDb + "&col=" + col + "&id=" + id;
        // System.out.println("DEBUG: Fetching " + url);
        
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .header("Authorization", token != null ? token : "")
            .GET()
            .build();
            
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
             System.out.println("Versions:");
             System.out.println(res.body());
        } else {
             System.out.println("Error: " + res.body());
        }
    }

    private static void handleRevert(String arg) throws Exception {
        if (currentDb == null) { System.out.println("No db selected."); return; }
        String[] parts = arg.split("\\s+");
        if (parts.length < 3) {
            System.out.println("Usage: revert <col> <id> <version>");
            return;
        }
        String col = parts[0];
        String id = parts[1];
        String version = parts[2];
        
        Map<String, String> body = new java.util.HashMap<>();
        body.put("db", currentDb);
        body.put("col", col);
        body.put("id", id);
        body.put("version", version);
        
        String json = mapper.writeValueAsString(body);
        
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/restore-version"))
            .header("Authorization", token != null ? token : "")
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(json))
            .build();
            
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         if (res.statusCode() == 200) {
             System.out.println("Version restored.");
        } else {
             System.out.println("Error: " + res.body());
        }
    }

    private static void handleCreateUser(String arg) throws Exception {
        if (token == null) { System.out.println("Not logged in (Admin required)."); return; }
        
        String[] parts = arg.split("\\s+");
        String username = "";

        if (parts.length > 1) {
            username = parts[1];
        } else {
            username = reader.readLine("Username: ").trim();
        }
        
        if (username.isEmpty()) return;
        
        String password = reader.readLine("Password: ", '*').trim();
        
        String role = reader.readLine("Role (admin, owner, writereader, reader) [reader]: ").trim();
        if (role.isEmpty()) role = "reader";
        
        String dbs = reader.readLine("Allowed DBs (comma separated, * for all) [*]: ").trim();
        if (dbs.isEmpty()) dbs = "*";
        
        java.util.List<String> allowedDbs = java.util.Arrays.asList(dbs.split(","));
        
        Map<String, Object> user = new java.util.HashMap<>();
        user.put("username", username);
        user.put("password", password);
        user.put("role", role);
        user.put("allowed_dbs", allowedDbs);
        
        String json = mapper.writeValueAsString(user);
        
        HttpRequest req = HttpRequest.newBuilder()
                 .uri(URI.create(baseUrl + "/api/users"))
                 .header("Authorization", token)
                 .header("Content-Type", "application/json")
                 .POST(HttpRequest.BodyPublishers.ofString(json))
                 .build();
                 
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            System.out.println("User created.");
        } else {
             System.out.println("Failed: " + res.body());
        }
    }

    private static void handleLogin(String arg) throws Exception {
        String[] creds = arg.split("\\s+");
        if (creds.length != 2) {
            System.out.println("Usage: login <username> <password>");
            return;
        }
        String json = mapper.writeValueAsString(Map.of("username", creds[0], "password", creds[1]));
        
        String loginUrl = federatedMode ? baseUrl + "/federated/login" : baseUrl + "/api/login";
        
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(loginUrl))
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

    private static void handleConnectCommand(String arg) {
        String[] parts = arg.trim().split("\\s+");
        if (parts.length < 2) {
            System.out.println("Usage: connect <node|federated> <url>");
            return;
        }
        String type = parts[0].toLowerCase();
        String url = parts[1];
        
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            url = "http://" + url;
        }
        
        baseUrl = url;
        token = null;
        currentDb = null;
        
        if (type.equals("federated")) {
            federatedMode = true;
            System.out.println("Connected to Federated Server at " + baseUrl);
            System.out.println("Only 'federated' commands are available in this mode.");
        } else if (type.equals("node")) {
            federatedMode = false;
            System.out.println("Connected to Database Node at " + baseUrl);
        } else {
            System.out.println("Invalid connection type. Use 'node' or 'federated'.");
        }
        // Reset client
        client = HttpClient.newHttpClient();
    }

    private static void handleShow(String arg) throws Exception {
        if (token == null) {
            System.out.println("Not logged in.");
            return;
        }
        if (arg.equals("dbs") || arg.equals("databases")) {
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/dbs"))
                .header("Authorization", token)
                .GET()
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             if (res.statusCode() == 200) {
                java.util.List<java.util.Map<String, String>> dbs = mapper.readValue(res.body(), new com.fasterxml.jackson.core.type.TypeReference<java.util.List<java.util.Map<String, String>>>() {});
                System.out.println("Databases:");
                for (java.util.Map<String, String> db : dbs) {
                    System.out.printf(" - %-20s [%s]%n", db.get("name"), db.get("engine"));
                }
            } else {
                System.out.println("Error: " + res.body());
            }
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
         Map<String, String> body = new java.util.HashMap<>();
         body.put("name", parts[1]);
         // Check for engine arg?
         // Shell parsing "create db mydb engine JettraEngineStore" ?
         // parts is only size 2 split by space limit 2.
         // "create" "db mydb engine ..."
         // The arg passed to handleCreate is "db name ..."
         // parts[0] is "db", parts[1] is "name ..."
         
         String[] dbParts = parts[1].split("\\s+");
         String dbName = dbParts[0];
         body.put("name", dbName);
         
         if (dbParts.length > 2 && dbParts[1].equalsIgnoreCase("engine")) {
             body.put("engine", dbParts[2]);
         }
         
         String json = mapper.writeValueAsString(body);
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

          
          String uri = baseUrl + "/api/doc?db=" + currentDb + "&col=" + col;
          if (currentTx != null) uri += "&tx=" + currentTx;

          HttpRequest req = HttpRequest.newBuilder()
             .uri(URI.create(uri))
             .header("Authorization", token)
             .header("Content-Type", "application/json")
             .POST(HttpRequest.BodyPublishers.ofString(docJson))
             .build();
          HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
          System.out.println(res.statusCode() == 200 ? "Inserted" : "Failed: " + res.body());
    }

    private static void handleDelete(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected"); return; }
         
         String[] parts = arg.split("\\s+");
         if (parts.length < 2) { System.out.println("Usage: delete <col> <id>"); return; }
         
         String col = parts[0];
         String id = parts[1];
         
         String uri = baseUrl + "/api/doc?db=" + currentDb + "&col=" + col + "&id=" + id;
         if (currentTx != null) uri += "&tx=" + currentTx;
         
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(uri))
            .header("Authorization", token)
            .DELETE()
            .build();
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         System.out.println(res.statusCode() == 200 ? "Deleted" : "Failed: " + res.body());
    }

    private static void handleBeginTx() throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentTx != null) { System.out.println("Transaction already active: " + currentTx); return; }
         
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tx/begin"))
            .header("Authorization", token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         if (res.statusCode() == 200) {
             Map<String, Object> map = mapper.readValue(res.body(), Map.class);
             currentTx = (String) map.get("txID");
             System.out.println("Transaction started: " + currentTx);
         } else {
             System.out.println("Failed to start transaction: " + res.body());
         }
    }

    private static void handleCommitTx() throws Exception {
         if (currentTx == null) { System.out.println("No active transaction."); return; }
         
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tx/commit?txID=" + currentTx))
            .header("Authorization", token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         if (res.statusCode() == 200) {
             System.out.println("Transaction committed.");
             currentTx = null;
         } else {
             System.out.println("Failed to commit: " + res.body());
         }
    }

    private static void handleRollbackTx() throws Exception {
         if (currentTx == null) { System.out.println("No active transaction."); return; }
         
         HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/api/tx/rollback?txID=" + currentTx))
            .header("Authorization", token)
            .POST(HttpRequest.BodyPublishers.noBody())
            .build();
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         if (res.statusCode() == 200) {
             System.out.println("Transaction rolled back.");
             currentTx = null;
         } else {
             System.out.println("Failed to rollback: " + res.body());
         }
    }

    private static void handleCount(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected"); return; }
         
         String col = arg.trim();
         if (col.isEmpty()) { System.out.println("Usage: count <col>"); return; }
         
         int count = getCount(currentDb, col);
         System.out.println("Count: " + count);
    }
    
    private static int getCount(String db, String col) {
        try {
            HttpRequest req = HttpRequest.newBuilder()
                 .uri(URI.create(baseUrl + "/api/count?db=" + db + "&col=" + col))
                 .header("Authorization", token)
                 .GET()
                 .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             if (res.statusCode() == 200) {
                 Map<String, Object> map = mapper.readValue(res.body(), Map.class);
                 return (int) map.get("count");
             }
        } catch (Exception e) {}
        return 0; 
    }

    private static void handleFind(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected"); return; }
         
         String col = arg;
         int limit = 10;
         int offset = 0;
         
         int total = getCount(currentDb, col);

         while (true) {
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/query?db=" + currentDb + "&col=" + col + "&limit=" + limit + "&offset=" + offset))
                .header("Authorization", token)
                .GET()
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             if (res.statusCode() != 200) {
                 System.out.println("Error (" + res.statusCode() + "): " + res.body());
                 return;
             }
             
             // Print results
             try {
                Object val = mapper.readValue(res.body(), Object.class);
                System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(val));
                
                int totalPages = (int) Math.ceil((double)total / limit);
                int currentPage = (offset / limit) + 1;
                
                System.out.println("\n--- Page " + currentPage + " of " + (totalPages > 0 ? totalPages : "?") + " (Offset " + offset + ", Total " + total + ") ---");
                String action = reader.readLine("[N]ext [B]ack [F]irst [L]ast [Q]uit > ").trim().toLowerCase();
                
                if (action.startsWith("n")) {
                    if (offset + limit < total) offset += limit;
                    else System.out.println("No more results.");
                } else if (action.startsWith("b")) {
                    offset = Math.max(0, offset - limit);
                } else if (action.startsWith("f")) {
                    offset = 0;
                } else if (action.startsWith("l")) {
                     offset = (totalPages - 1) * limit;
                     if (offset < 0) offset = 0;
                } else if (action.startsWith("q")) {
                    break;
                }
             } catch (Exception e) {
                 System.out.println(res.body());
                 break;
             }
         }
    }

    private static void handleBackup(String arg) throws Exception {
        if (token == null) { System.out.println("Not logged in."); return; }
        
        String db = arg.isEmpty() ? currentDb : arg;
        if (db == null) {
            System.out.println("No database specified. Usage: backup <dbname> or use <dbname> first.");
            return;
        }

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/backup?db=" + db))
                .header("Authorization", token)
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();
        
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        if (res.statusCode() == 200) {
            Map<String, String> map = mapper.readValue(res.body(), Map.class);
            System.out.println("Backup created: " + map.get("file"));
        } else {
             System.out.println("Backup failed: " + res.body());
        }
    }

    private static void handleRestore(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         
         // Args: <file> <targetDb>
         String[] parts = arg.trim().split("\\s+");
         if (parts.length < 2) {
             System.out.println("Usage: restore <file.zip> <targetDb>");
             return;
         }
         
         String filePath = parts[0];
         String targetDb = parts[1];
         
         java.nio.file.Path path = java.nio.file.Paths.get(filePath);
         if (!java.nio.file.Files.exists(path)) {
             System.out.println("File not found: " + filePath);
             return;
         }
         
         System.out.println("Uploading and restoring " + filePath + " to " + targetDb + "...");
         
         HttpRequest req = HttpRequest.newBuilder()
                 .uri(URI.create(baseUrl + "/api/restore/upload?db=" + targetDb))
                 .header("Authorization", token)
                 .header("Content-Type", "application/octet-stream")
                 .POST(HttpRequest.BodyPublishers.ofFile(path))
                 .build();
         
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         if (res.statusCode() == 200) {
             System.out.println("Restore successful!");
             // Optionally print details if returned JSON
         } else {
             System.out.println("Restore failed: " + res.body());
         }
    }
    private static void handleExport(String arg) throws Exception {
         if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected."); return; }
         
         // Usage: export <col> <json|csv> <filename>
         String[] parts = arg.trim().split("\\s+");
         if (parts.length < 3) {
             System.out.println("Usage: export <collection> <format> <filename>");
             return;
         }
         
         String col = parts[0];
         String format = parts[1];
         String filename = parts[2];
         
         System.out.println("Exporting " + col + " to " + filename + " as " + format + "...");
         
         HttpRequest req = HttpRequest.newBuilder()
                 .uri(URI.create(baseUrl + "/api/export?db=" + currentDb + "&col=" + col + "&format=" + format))
                 .header("Authorization", token)
                 .GET()
                 .build();
         
         HttpResponse<java.nio.file.Path> res = client.send(req, HttpResponse.BodyHandlers.ofFile(java.nio.file.Paths.get(filename)));
         
         if (res.statusCode() == 200) {
             System.out.println("Export successful: " + filename);
         } else {
             System.out.println("Export failed: " + res.statusCode());
             // Clean up empty file?
         }
    }
    
    private static void handleImport(String arg) throws Exception {
        if (token == null) { System.out.println("Not logged in."); return; }
         if (currentDb == null) { System.out.println("No DB selected."); return; }
         
         // Usage: import <col> <json|csv> <filename>
         String[] parts = arg.trim().split("\\s+");
         if (parts.length < 3) {
             System.out.println("Usage: import <collection> <format> <filename>");
             return;
         }
         
         String col = parts[0];
         String format = parts[1];
         String filename = parts[2];
         
         java.nio.file.Path path = java.nio.file.Paths.get(filename);
         if (!java.nio.file.Files.exists(path)) {
             System.out.println("File not found: " + filename);
             return;
         }
         
         System.out.println("Importing " + filename + " to " + col + "...");
         
         HttpRequest req = HttpRequest.newBuilder()
                 .uri(URI.create(baseUrl + "/api/import?db=" + currentDb + "&col=" + col + "&format=" + format))
                 .header("Authorization", token)
                 .header("Content-Type", "application/octet-stream")
                 .POST(HttpRequest.BodyPublishers.ofFile(path))
                 .build();
         
         HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
         if (res.statusCode() == 200) {
             System.out.println("Import successful: " + res.body());
         } else {
             System.out.println("Import failed: " + res.body());
         }
    }

    private static void paginateList(java.util.List<?> list) throws Exception {
        int total = list.size();
        int limit = 10;
        int offset = 0;
        
        if (total <= limit) {
             System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(list));
             return;
        }

        while (true) {
             int end = Math.min(offset + limit, total);
             java.util.List<?> page = list.subList(offset, end);
             
             System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(page));
             
             int totalPages = (int) Math.ceil((double)total / limit);
             int currentPage = (offset / limit) + 1;
             
             System.out.println("\n--- Page " + currentPage + " of " + totalPages + " (Offset " + offset + ", Total " + total + ") ---");
             String action = reader.readLine("[N]ext [B]ack [F]irst [L]ast [Q]uit > ").trim().toLowerCase();
             
             if (action.startsWith("n")) {
                 if (offset + limit < total) offset += limit;
                 else System.out.println("End of results.");
             } else if (action.startsWith("b")) {
                 offset = Math.max(0, offset - limit);
             } else if (action.startsWith("f")) {
                 offset = 0;
             } else if (action.startsWith("l")) {
                 offset = (totalPages - 1) * limit;
                 if (offset < 0) offset = 0;
             } else if (action.startsWith("q")) {
                 break;
             }
        }
    }
        private static void handleCluster(String arg) throws Exception {
         String[] parts = arg.trim().split("\\s+");
         if (parts.length < 1) { System.out.println("Usage: cluster <status|add|remove> [url]"); return; }
         
         String subCmd = parts[0].toLowerCase();
         
         if (subCmd.equals("status")) {
             System.out.println("Command removed. Use federated commands for cluster info.");
         } else if (subCmd.equals("add")) {
             System.out.println("Command removed. Use federated commands.");
         } else if (subCmd.equals("remove")) {
             System.out.println("Command removed. Use federated commands.");
         } else if (subCmd.equals("pause")) {
             if (parts.length < 2) { System.out.println("Usage: cluster pause <nodeNameOrUrl>"); return; }
             String node = parts[1];
             String json = mapper.writeValueAsString(Map.of("node", node)); // API expects 'node'
             
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/cluster/pause"))
                .header("Authorization", token != null ? token : "")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             System.out.println(res.statusCode() == 200 ? "Node paused: " + res.body() : "Error: " + res.body());
         } else if (subCmd.equals("resume")) {
             if (parts.length < 2) { System.out.println("Usage: cluster resume <nodeNameOrUrl>"); return; }
             String node = parts[1];
             String json = mapper.writeValueAsString(Map.of("node", node));
             
             HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/cluster/resume"))
                .header("Authorization", token != null ? token : "")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();
             HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
             System.out.println(res.statusCode() == 200 ? "Node resumed: " + res.body() : "Error: " + res.body());
         } else {
             System.out.println("Unknown cluster command: " + subCmd);
         }
    }

    private static void handleFederated(String arg) {
        if (arg == null || arg.trim().isEmpty()) {
            System.out.println("Usage: federated <show|leader|nodes|node-leader>");
            return;
        }

        String[] parts = arg.trim().split("\\s+");
        String subCmd = parts[0].toLowerCase();
        String subArg = parts.length > 1 ? parts[1] : "";

        try {
            switch (subCmd) {
                case "show":
                    handleFederatedShow();
                    break;
                case "leader":
                    handleFederatedLeader();
                    break;
                case "nodes":
                    handleFederatedNodes();
                    break;
                case "node-leader":
                    handleNodeLeader();
                    break;
                case "stop":
                    handleFederatedStop(subArg);
                    break;
                case "remove":
                    handleFederatedRemove(subArg);
                    break;
                case "add":
                    handleFederatedAdd(subArg);
                    break;
                default:
                    System.out.println("Unknown federated command: " + subCmd);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private static void handleFederatedShow() throws Exception {
        String url = federatedMode ? baseUrl + "/federated/status" : baseUrl + "/api/federated";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token != null ? token : "")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Map<String, Object> status = mapper.readValue(response.body(), Map.class);
            String leaderId = (String) status.get("raftLeaderId");
            System.out.println("Federated Cluster Status:");
            System.out.println("Leader: " + (leaderId != null ? leaderId : "None"));
            System.out.println("Term:   " + status.get("raftTerm"));
            System.out.println("----------------------------------------------------------------------");
            System.out.printf("%-15s | %-25s | %-10s | %-15s\n", "Server ID", "URL", "Raft State", "Last Seen");
            
            @SuppressWarnings("unchecked")
            java.util.List<String> peers = (java.util.List<String>) status.get("raftPeers");
            @SuppressWarnings("unchecked")
            Map<String, String> peerIds = (Map<String, String>) status.get("raftPeerIds");
            @SuppressWarnings("unchecked")
            Map<String, String> peerStates = (Map<String, String>) status.get("raftPeerStates");
            @SuppressWarnings("unchecked")
            Map<String, Object> peerSeen = (Map<String, Object>) status.get("raftPeerLastSeen");

            // Self info
            System.out.printf("%-15s | %-25s | %-10s | %-15s (Self)\n", 
                status.get("raftSelfId"), status.get("raftSelfUrl"), status.get("raftState"), "Now");

            if (peers != null) {
                for (String pUrl : peers) {
                    if (pUrl.equals(status.get("raftSelfUrl"))) continue;
                    String id = peerIds != null ? peerIds.get(pUrl) : "unknown";
                    String state = peerStates != null ? peerStates.get(pUrl) : "unknown";
                    Object seen = peerSeen != null ? peerSeen.get(pUrl) : "-";
                    System.out.printf("%-15s | %-25s | %-10s | %-15s\n", id, pUrl, state, seen);
                }
            }
        } else {
            System.out.println("Error: " + response.body());
        }
    }

    private static void handleFederatedLeader() throws Exception {
        String url = federatedMode ? baseUrl + "/federated/status" : baseUrl + "/api/federated";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token != null ? token : "")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Map<String, Object> status = mapper.readValue(response.body(), Map.class);
            System.out.println("Federated Leader Information:");
            System.out.println("  Leader ID: " + status.get("leaderId"));
            String leaderUrl = (String) status.get("raftLeaderUrl");
            if (leaderUrl != null) {
                try {
                    URI uri = new URI(leaderUrl);
                    System.out.println("  Leader IP: " + uri.getHost());
                    System.out.println("  Leader Port: " + uri.getPort());
                } catch (Exception e) {
                    System.out.println("  Leader URL: " + leaderUrl);
                }
            }
            System.out.println("  Raft State: " + status.get("raftState"));
            System.out.println("  Raft Term: " + status.get("raftTerm"));
        } else {
            System.out.println("Error: " + response.body());
        }
    }

    private static void handleFederatedNodes() throws Exception {
        String url = federatedMode ? baseUrl + "/federated/status" : baseUrl + "/api/federated";
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token != null ? token : "")
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            Map<String, Object> status = mapper.readValue(response.body(), Map.class);
            String dbLeaderId = (String) status.get("leaderId"); 
            
            System.out.println("Federated Network Nodes (DB Cluster):");
            System.out.println("DB Leader ID: " + dbLeaderId);
            System.out.println("----------------------------------------------------------------------");
            @SuppressWarnings("unchecked")
            java.util.List<Map<String, Object>> nodes = (java.util.List<Map<String, Object>>) status.get("nodes");
            if (nodes != null) {
                System.out.printf("%-15s | %-25s | %-10s | %-10s\n", "Node ID", "URL", "Status", "Role");
                for (Map<String, Object> node : nodes) {
                    String id = (String) node.get("id");
                    String nodeUrl = (String) node.get("url");
                    String nStatus = (String) node.get("status");
                    String role = id.equals(dbLeaderId) ? "LEADER" : "FOLLOWER";
                    System.out.printf("%-15s | %-25s | %-10s | %-10s\n", id, nodeUrl, nStatus, role);
                }
            }
        } else {
            System.out.println("Error: " + response.body());
        }
    }

    private static void handleNodeLeader() {
        try {
            String url = federatedMode ? baseUrl + "/federated/node-leader" : baseUrl + "/api/federated/node-leader";
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", token != null ? token : "")
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 200) {
                Map<String, Object> node = mapper.readValue(response.body(), Map.class);
                System.out.println("Current Database Leader (Detailed):");
                System.out.println("  ID:     " + node.get("id"));
                System.out.println("  URL:    " + node.get("url"));
                System.out.println("  Status: " + node.get("status"));
                if (node.get("metrics") != null) {
                    Map<String, Object> metrics = (Map<String, Object>) node.get("metrics");
                    System.out.println("  CPU:    " + metrics.get("cpuUsage") + "%");
                    System.out.println("  RAM:    " + metrics.get("ramUsedStr") + " / " + metrics.get("ramTotalStr"));
                }
            } else {
                System.out.println("Error: " + response.body());
            }
        } catch (Exception e) {
            System.out.println("Error fetching node leader: " + e.getMessage());
        }
    }

    private static void handleFederatedStop(String targetUrl) throws Exception {
        if (targetUrl.isEmpty()) {
            targetUrl = baseUrl;
        } else if (!targetUrl.startsWith("http")) {
            targetUrl = "http://" + targetUrl;
        }

        String confirm = reader.readLine("Are you sure you want to stop federated server at " + targetUrl + "? (y/n): ").trim().toLowerCase();
        if (!confirm.equals("y") && !confirm.equals("yes")) {
            System.out.println("Canceled.");
            return;
        }

        String stopUrl = targetUrl.endsWith("/federated/stop") ? targetUrl : 
                        (targetUrl.endsWith("/") ? targetUrl + "federated/stop" : targetUrl + "/federated/stop");

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(stopUrl))
                .header("Authorization", token != null ? token : "")
                .POST(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            System.out.println("Stop command sent successfully.");
        } else {
            System.out.println("Error (" + response.statusCode() + "): " + response.body());
        }
    }

    private static void handleFederatedRemove(String targetUrl) throws Exception {
        if (targetUrl.isEmpty()) {
            System.out.println("Usage: federated remove <url>");
            return;
        }
        if (!targetUrl.startsWith("http")) {
            targetUrl = "http://" + targetUrl;
        }

        String confirm = reader.readLine("Are you sure you want to remove federated server " + targetUrl + " from the cluster? (y/n): ").trim().toLowerCase();
        if (!confirm.equals("y") && !confirm.equals("yes")) {
            System.out.println("Canceled.");
            return;
        }

        // Send removal request to CURRENT leader or connected server
        String removeUrl = federatedMode ? baseUrl + "/federated/raft/removePeer" : baseUrl + "/api/federated/raft/removePeer";
        
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(removeUrl))
                .header("Authorization", token != null ? token : "")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(Map.of("url", targetUrl))))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            System.out.println("Peer removal initiated.");
        } else {
            System.out.println("Error (" + response.statusCode() + "): " + response.body());
        }
    }

    private static void handleFederatedAdd(String targetUrl) throws Exception {
        if (targetUrl.isEmpty()) {
            System.out.println("Usage: federated add <url>");
            return;
        }
        if (!targetUrl.startsWith("http")) targetUrl = "http://" + targetUrl;

        String url = federatedMode ? baseUrl + "/federated/raft/addPeer" : baseUrl + "/api/federated/raft/addPeer";
        
        Map<String, String> body = Map.of("url", targetUrl);
        String json = mapper.writeValueAsString(body);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Authorization", token != null ? token : "")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() == 200) {
            System.out.println("Add command sent successfully: " + response.body());
        } else {
            System.out.println("Failed to add peer: " + response.body());
        }
    }
}

