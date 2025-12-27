package io.jettra.memory.shell;

import io.jettra.memory.driver.JettraMemoryDriver;
import org.jline.reader.Completer;
import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.reader.impl.completer.StringsCompleter;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import com.fasterxml.jackson.databind.ObjectMapper;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;
import java.util.Map;

@Command(name = "jettra-memory-shell", mixinStandardHelpOptions = true, version = "1.0",
         description = "Shell to interact with JettraMemoryDB Server")
public class JettraMemoryShell implements Runnable {

    @Option(names = {"-H", "--host"}, description = "Server host", defaultValue = "localhost")
    private String host;

    @Option(names = {"-p", "--port"}, description = "Server port", defaultValue = "7000")
    private int port;

    @Option(names = {"-u", "--user"}, description = "Username", defaultValue = "admin")
    private String username;

    @Option(names = {"-P", "--pass"}, description = "Password", defaultValue = "admin")
    private String password;

    private JettraMemoryDriver driver;
    private String currentDb = null;
    private String currentTx = null;
    private final ObjectMapper mapper = new ObjectMapper();

    public static void main(String[] args) {
        int exitCode = new CommandLine(new JettraMemoryShell()).execute(args);
        System.exit(exitCode);
    }

    @Override
    public void run() {
        System.out.println("""
               _      _   _            ____  ______      ____  __
              | |    | | | |          |  _ \\|  _ \\ \\    / /  \\/  |
              | | ___| |_| |_ _ __ __ | | | | |_) \\ \\  / /| \\  / |
          _   | |/ _ \\ __| __| '__/ _` | | | |  _ < \\ \\/ / | |\\/| |
         | |__| |  __/ |_| |_| | | (_| | |_| | |_) | \\  /  | |  | |
          \\____/ \\___|\\__|\\__|_|  \\__,_|____/|____/   \\/   |_|  |_|
                                                                   
        Jettra Memory Shell v1.0 (Networking Mode)
        """);
        
        driver = new JettraMemoryDriver(host, port, username, password);
        System.out.println("Connected to " + host + ":" + port + " as " + username);
        System.out.println("Type 'help' for commands.");

        try {
            Terminal terminal = TerminalBuilder.builder().system(true).build();
            Completer commandCompleter = new StringsCompleter(
                "use", "show", "create", "insert", "find", "delete", "exit", "quit", "help", "clear", "cls",
                "begin", "commit", "rollback", "history", "revert"
            );
            
            LineReader reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .completer(commandCompleter)
                .build();

            while (true) {
                String prompt = "jettra-memory";
                if (currentDb != null) prompt += ":" + currentDb;
                prompt += "> ";
                if (currentTx != null) prompt += "(TX:" + currentTx + ") ";

                String line;
                try {
                    line = reader.readLine(prompt);
                } catch (UserInterruptException e) {
                    continue;
                } catch (EndOfFileException e) {
                    break;
                }

                line = line.trim();
                if (line.isEmpty()) continue;
                if ("exit".equalsIgnoreCase(line) || "quit".equalsIgnoreCase(line)) break;

                processCommand(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processCommand(String line) {
        String[] parts = line.split("\\s+", 3);
        String cmd = parts[0].toLowerCase();
        
        try {
            switch (cmd) {
                case "help": printHelp(); break;
                case "cls":
                case "clear": System.out.print("\033[H\033[2J"); System.out.flush(); break;
                case "use": 
                    if (parts.length < 2) System.out.println("Usage: use <db>");
                    else { currentDb = parts[1]; System.out.println("Switched to db " + currentDb); }
                    break;
                case "show": handleShow(parts); break;
                case "create": handleCreate(parts); break;
                case "insert": handleInsert(parts); break;
                case "find": handleFind(parts); break;
                case "delete": handleDelete(parts); break;
                case "begin": handleBegin(); break;
                case "commit": handleCommit(); break;
                case "rollback": handleRollback(); break;
                case "history": handleHistory(parts); break;
                case "revert": handleRevert(parts); break;
                default: System.out.println("Unknown command: " + cmd);
            }
        } catch (Exception e) {
            System.out.println("Error: " + e.getMessage());
        }
    }

    private void handleShow(String[] parts) {
        if (parts.length < 2) { System.out.println("Usage: show dbs | collections"); return; }
        String type = parts[1].toLowerCase();
        if ("dbs".equals(type) || "databases".equals(type)) {
            List<String> dbs = driver.listDatabases();
            System.out.println("Databases:");
            dbs.forEach(db -> System.out.println("  - " + db));
        } else if ("collections".equals(type)) {
            if (currentDb == null) { System.out.println("No DB selected. Use 'use <db>'"); return; }
            // Assuming listCollections is available or list by db
            // Re-using listDatabases logic for collections or similar
            System.out.println("Collections in " + currentDb + ":");
            // Driver needs listCollections method
        }
    }
    
    private void handleCreate(String[] parts) {
        if (parts.length < 3) { System.out.println("Usage: create db <name> | col <name>"); return; }
        String type = parts[1].toLowerCase();
        String name = parts[2];
        if ("db".equals(type)) {
            driver.createDatabase(name);
            System.out.println("Database created: " + name);
        } else if ("col".equals(type)) {
            if (currentDb == null) { System.out.println("No DB selected."); return; }
            driver.createCollection(currentDb, name);
            System.out.println("Collection created: " + name);
        }
    }

    private void handleInsert(String[] parts) throws Exception {
        if (parts.length < 3) { System.out.println("Usage: insert <col> <json>"); return; }
        if (currentDb == null) { System.out.println("No DB selected."); return; }
        String col = parts[1];
        String json = parts[2];
        Map<String, Object> doc = mapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        String id = driver.saveDocument(currentDb, col, doc);
        System.out.println("Inserted ID: " + id);
    }

    private void handleFind(String[] parts) throws Exception {
        if (parts.length < 2) { System.out.println("Usage: find <col>"); return; }
        if (currentDb == null) { System.out.println("No DB selected."); return; }
        String col = parts[1];
        List<Map<String, Object>> results = driver.query(currentDb, col, 100, 0);
        System.out.println("Results (" + results.size() + "):");
        for (Map<String, Object> doc : results) {
            System.out.println(mapper.writerWithDefaultPrettyPrinter().writeValueAsString(doc));
        }
    }

    private void handleDelete(String[] parts) {
        if (parts.length < 3) { System.out.println("Usage: delete <col> <id>"); return; }
        if (currentDb == null) { System.out.println("No DB selected."); return; }
        driver.deleteDocument(currentDb, parts[1], parts[2]);
        System.out.println("Deleted.");
    }

    private void handleBegin() {
        currentTx = driver.beginTransaction();
        System.out.println("Transaction started: " + currentTx);
    }

    private void handleCommit() {
        if (currentTx == null) { System.out.println("No active transaction."); return; }
        driver.commitTransaction(currentTx);
        System.out.println("Transaction committed.");
        currentTx = null;
    }

    private void handleRollback() {
        if (currentTx == null) { System.out.println("No active transaction."); return; }
        driver.rollbackTransaction(currentTx);
        System.out.println("Transaction rolled back.");
        currentTx = null;
    }

    private void handleHistory(String[] parts) {
        if (parts.length < 3) { System.out.println("Usage: history <col> <id>"); return; }
        if (currentDb == null) { System.out.println("No DB selected."); return; }
        List<String> versions = driver.getVersions(currentDb, parts[1], parts[2]);
        System.out.println("Versions for " + parts[2] + ":");
        versions.forEach(v -> System.out.println("  - " + v));
    }

    private void handleRevert(String[] parts) {
        if (parts.length < 4) { System.out.println("Usage: revert <col> <id> <version>"); return; }
        if (currentDb == null) { System.out.println("No DB selected."); return; }
        driver.restoreVersion(currentDb, parts[1], parts[2], parts[3]);
        System.out.println("Reverted to version " + parts[3]);
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  use <db>               Select database");
        System.out.println("  show dbs               List databases");
        System.out.println("  show collections       List collections in current db");
        System.out.println("  create db <name>       Create database");
        System.out.println("  create col <name>      Create collection");
        System.out.println("  insert <col> <json>    Insert document");
        System.out.println("  find <col>             Find all documents");
        System.out.println("  delete <col> <id>      Delete document");
        System.out.println("  begin                  Start transaction");
        System.out.println("  commit                 Commit transaction");
        System.out.println("  rollback               Rollback transaction");
        System.out.println("  history <col> <id>     Show version history");
        System.out.println("  revert <col> <id> <v>  Revert document to version");
        System.out.println("  cls / clear            Clear screen");
        System.out.println("  exit / quit            Exit shell");
    }
}
