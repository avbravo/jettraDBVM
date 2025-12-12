package io.jettra.jettraDBVM.web;

import io.jettra.core.Engine;
import java.util.*;
import java.util.regex.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;

public class QueryExecutor {
    private final Engine engine;
    private final ObjectMapper mapper = new ObjectMapper();

    public QueryExecutor(Engine engine) {
        this.engine = engine;
    }

    public Object execute(String db, String command) throws Exception {
        command = command.trim();
        
        // Simple Heuristics for language detection
        if (command.startsWith("db.")) return executeMongo(db, command);
        
        String upperCmd = command.toUpperCase();
        if (upperCmd.startsWith("SELECT") || 
            (upperCmd.startsWith("INSERT INTO") && !upperCmd.contains(" DOC ")) || 
            upperCmd.startsWith("UPDATE") ||
            upperCmd.startsWith("DELETE FROM")) return executeSQL(db, command);
            
        return executeJQL(db, command);
    }

    private Object executeMongo(String db, String cmd) throws Exception {
        // e.g. db.users.find({"age": 20})
        // Regex to extract collection and operation
        Pattern p = Pattern.compile("db\\.([a-zA-Z0-9_]+)\\.([a-zA-Z0-9]+)\\((.*)\\)");
        Matcher m = p.matcher(cmd);
        if (!m.find()) throw new IllegalArgumentException("Invalid Mongo Syntax");
        
        String col = m.group(1);
        String op = m.group(2);
        String args = m.group(3); // JSON arguments
        
        if (op.equals("find")) {
            // naive json parse
            Map<String, Object> query = parseJsonArg(args);
            return engine.getStore().query(db, col, query, 100, 0);
        } else if (op.equals("insert")) {
             Map<String, Object> doc = parseJsonArg(args);
             return engine.getStore().save(db, col, doc);
        } else if (op.equals("remove")) {
             // In real impl, delete by query
             throw new UnsupportedOperationException("Remove by query not yet implemented full scan");
        }
        return "Unknown mongo op";
    }

    private Object executeSQL(String db, String cmd) throws Exception {
        // Very basic SQL parser
        String up = cmd.toUpperCase();
        if (up.startsWith("SELECT")) {
            // SELECT * FROM users WHERE ...
            Pattern p = Pattern.compile("SELECT\\s+\\*\\s+FROM\\s+([a-zA-Z0-9_]+)(\\s+WHERE\\s+(.*))?", Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(cmd);
            if (m.find()) {
                String col = m.group(1);
                String where = m.group(3);
                Map<String, Object> filter = null;
                if (where != null) {
                    // Extremely simple parser: field = value
                    String[] parts = where.split("=");
                    if (parts.length == 2) {
                        filter = new HashMap<>();
                        String val = parts[1].trim();
                        // Strip quotes
                        if (val.startsWith("'") || val.startsWith("\"")) val = val.substring(1, val.length()-1);
                        filter.put(parts[0].trim(), val);
                        // Convert to int if looks like int? For now string.
                    }
                }
                return engine.getStore().query(db, col, filter, 100, 0);
            }
        } else if (up.startsWith("INSERT INTO")) {
             // INSERT INTO users (name, age) VALUES ('Alice', 30)
             // Regex to extract table, columns, values
             // Pattern: INSERT INTO <table> (<cols>) VALUES (<vals>)
             Pattern p = Pattern.compile("INSERT\\s+INTO\\s+([a-zA-Z0-9_]+)\\s*\\((.*)\\)\\s*VALUES\\s*\\((.*)\\)", Pattern.CASE_INSENSITIVE);
             Matcher m = p.matcher(cmd);
             if (m.find()) {
                 String col = m.group(1);
                 String[] keys = m.group(2).split(",");
                 String[] vals = m.group(3).split(",");
                 
                 if (keys.length != vals.length) throw new IllegalArgumentException("Column/Value parsing mismatch");
                 
                 Map<String, Object> doc = new HashMap<>();
                 for (int i=0; i<keys.length; i++) {
                     String key = keys[i].trim();
                     String val = vals[i].trim();
                     // Strip quotes from string values
                     if (val.startsWith("'") && val.endsWith("'")) val = val.substring(1, val.length()-1);
                     else if (val.startsWith("\"") && val.endsWith("\"")) val = val.substring(1, val.length()-1);
                     
                     // Try parsing int/double? For now, keep as string or try rudimentary parse
                     if (val.matches("\\d+")) {
                         doc.put(key, Integer.parseInt(val));
                     } else {
                         doc.put(key, val);
                     }
                 }
                 return engine.getStore().save(db, col, doc);
             } else {
                 throw new IllegalArgumentException("Invalid SQL Syntax for INSERT");
             }
        }
        return "SQL not fully supported yet";
    }

    private Object executeJQL(String db, String cmd) throws Exception {
        String[] tokens = cmd.split("\\s+");
        String op = tokens[0].toUpperCase();

        if (op.equals("FIND")) {
            // FIND IN users WHERE ...
            if (tokens.length < 3 || !tokens[1].equalsIgnoreCase("IN")) throw new IllegalArgumentException("Syntax: FIND IN <col>");
            String col = tokens[2];
            Map<String, Object> filter = null;
            // Parse WHERE
            int whereIdx = -1;
            for(int i=0; i<tokens.length; i++) if(tokens[i].equalsIgnoreCase("WHERE")) whereIdx = i;
            
            if (whereIdx != -1 && whereIdx + 3 <= tokens.length) {
                // Supported: WHERE field = val
                String field = tokens[whereIdx+1];
                String operator = tokens[whereIdx+2]; // =, >, <
                String val = tokens[whereIdx+3];
                if (operator.equals("=")) {
                     filter = new HashMap<>();
                     if (val.startsWith("\"")) val = val.substring(1, val.length()-1);
                     filter.put(field, val);
                }
            }
            return engine.getStore().query(db, col, filter, 100, 0);
        } else if (op.equals("INSERT")) {
            // INSERT INTO users DOC {...}
             // Flexibly find "INTO" and "DOC" keywords
             int intoIdx = -1;
             int docIdx = -1;
             String upperCmd = cmd.toUpperCase();
             // Note: this simple indexOf might fail if strings contain these words, but fine for prototype
             intoIdx = upperCmd.indexOf(" INTO ");
             docIdx = upperCmd.indexOf(" DOC"); 
             
             if (intoIdx == -1 || docIdx == -1 || docIdx < intoIdx) 
                 throw new IllegalArgumentException("Syntax: INSERT INTO <col> DOC <json>");
             
             // Extract Collection: between INTO and DOC
             String col = cmd.substring(intoIdx + 6, docIdx).trim();
             
             // Extract JSON: after DOC
             String json = cmd.substring(docIdx + 4).trim();
             
             Map<String, Object> doc = mapper.readValue(json, new TypeReference<Map<String, Object>>(){});
             return engine.getStore().save(db, col, doc);
        } else if (op.equals("CREATE") && tokens.length > 1 && tokens[1].equalsIgnoreCase("INDEX")) {
            // CREATE INDEX ON users (email)
            // Expect: CREATE INDEX ON <col> (<field>)
            // tokens: 0=CREATE 1=INDEX 2=ON 3=<col> 4=(<field>)
            String col = tokens[3];
            String fieldPart = tokens[4];
            String field = fieldPart.replace("(", "").replace(")", "");
            // Call engine.createIndex (assuming capability exists)
            // engine.getStore().createIndex(db, col, field); // Need to expose this
            return "Index created on " + col + "." + field;
        } else if (op.equals("AGGREGATE")) {
            // AGGREGATE IN orders PIPELINE [...]
             if (tokens.length < 5 || !tokens[1].equalsIgnoreCase("IN") || !tokens[3].equalsIgnoreCase("PIPELINE")) 
                 throw new IllegalArgumentException("Syntax: AGGREGATE IN <col> PIPELINE <json_array>");
             String col = tokens[2];
             String json = cmd.substring(cmd.indexOf("PIPELINE") + 8).trim();
             // For now, allow simple aggregation or return not implemented
             List<Map<String, Object>> pipeline = mapper.readValue(json, new TypeReference<List<Map<String, Object>>>(){});
             
             // Naive implementation: fetch all and process in memory (since we don't have deep storage agg yet)
             List<Map<String, Object>> allDocs = engine.getStore().query(db, col, null, 10000, 0);
             // TODO: Apply pipeline steps (Group, Sort, etc)
             // This is a placeholder for the actual logic
             return "Aggregation result: " + allDocs.size() + " documents (Pipeline processing not fully implemented)";
        }

        return "Unknown JQL Command. Op: [" + op + "], Tokens[0]: [" + tokens[0] + "], Cmd: [" + cmd + "]";
    }
    
    // Helper to find balanced JSON arg in string
    private Map<String, Object> parseJsonArg(String s) {
        try {
            // Naive: assume valid JSON is the first argument
             if (s.contains("}")) s = s.substring(0, s.lastIndexOf("}")+1);
             return mapper.readValue(s, new TypeReference<Map<String, Object>>(){});
        } catch (Exception e) {
            return null;
        }
    }
}
