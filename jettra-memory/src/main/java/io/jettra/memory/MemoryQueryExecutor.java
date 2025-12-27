package io.jettra.memory;
 
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
 
public class MemoryQueryExecutor {
    private final JettraMemoryDB db;
    private final ObjectMapper mapper = new ObjectMapper();
 
    public MemoryQueryExecutor(JettraMemoryDB db) {
        this.db = db;
    }
 
    public Object execute(String dbName, String command) throws Exception {
        command = command.trim();
        
        if (command.startsWith("db.")) return executeMongo(dbName, command);
        
        String upperCmd = command.toUpperCase();
        if (upperCmd.startsWith("SELECT") || 
            (upperCmd.startsWith("INSERT INTO") && !upperCmd.contains(" DOC ")) || 
            upperCmd.startsWith("UPDATE") ||
            upperCmd.startsWith("DELETE FROM")) return executeSQL(dbName, command);
            
        return executeJQL(dbName, command);
    }
 
    private Object executeMongo(String dbName, String cmd) throws Exception {
        Pattern p = Pattern.compile("db\\.([a-zA-Z0-9_]+)\\.([a-zA-Z0-9]+)\\((.*)\\)");
        Matcher m = p.matcher(cmd);
        if (!m.find()) throw new IllegalArgumentException("Invalid Mongo Syntax");
        
        String colName = m.group(1);
        String op = m.group(2);
        String args = m.group(3);
        
        MemoryCollection col = db.getCollection(dbName, colName);
        if (col == null && op.equals("insert")) col = db.createCollection(dbName, colName);
        if (col == null) return "Collection not found";
 
        if (op.equals("find")) {
            return col.getAll().values(); // Naive find all for now
        } else if (op.equals("insert")) {
             Map<String, Object> doc = parseJsonArg(args);
             String id = (String) doc.get("_id");
             if (id == null) id = UUID.randomUUID().toString();
             doc.put("_id", id);
             col.insert(id, doc, 0);
             return Map.of("id", id, "status", "ok");
        }
        return "Unknown mongo op";
    }
 
    private Object executeSQL(String dbName, String cmd) throws Exception {
        String up = cmd.toUpperCase();
        if (up.startsWith("SELECT")) {
            Pattern p = Pattern.compile("SELECT\\s+\\*\\s+FROM\\s+([a-zA-Z0-9_]+)", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(cmd);
            if (m.find()) {
                String colName = m.group(1);
                MemoryCollection col = db.getCollection(dbName, colName);
                return (col != null) ? col.getAll().values() : List.of();
            }
        }
        return "SQL not fully supported in memory yet";
    }
 
    private Object executeJQL(String dbName, String cmd) throws Exception {
        String[] tokens = cmd.split("\\s+");
        String op = tokens[0].toUpperCase();
 
        if (op.equals("FIND")) {
            if (tokens.length < 3 || !tokens[1].equalsIgnoreCase("IN")) throw new IllegalArgumentException("Syntax: FIND IN <col>");
            String colName = tokens[2];
            MemoryCollection col = db.getCollection(dbName, colName);
            return (col != null) ? col.getAll().values() : List.of();
        } else if (op.equals("INSERT")) {
             int intoIdx = cmd.toUpperCase().indexOf(" INTO ");
             int docIdx = cmd.toUpperCase().indexOf(" DOC"); 
             
             if (intoIdx == -1 || docIdx == -1 || docIdx < intoIdx) 
                 throw new IllegalArgumentException("Syntax: INSERT INTO <col> DOC <json>");
             
             String colName = cmd.substring(intoIdx + 6, docIdx).trim();
             String json = cmd.substring(docIdx + 4).trim();
             
             Map<String, Object> doc = mapper.readValue(json, new TypeReference<Map<String, Object>>(){});
             String id = (String) doc.get("_id");
             if (id == null) id = UUID.randomUUID().toString();
             doc.put("_id", id);
             
             db.createCollection(dbName, colName).insert(id, doc, 0);
             return Map.of("id", id, "status", "ok");
        }
 
        return "Unknown JQL Command";
    }
 
    private Map<String, Object> parseJsonArg(String s) {
        try {
             if (s.contains("}")) s = s.substring(0, s.lastIndexOf("}")+1);
             return mapper.readValue(s, new TypeReference<Map<String, Object>>(){});
        } catch (Exception e) {
            return null;
        }
    }
}
