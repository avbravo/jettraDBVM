package io.jettra.core.raft;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import io.jettra.core.storage.DocumentStore;

/**
 * Manages the Raft log.
 * Stores log entries in the underlying DocumentStore under "_system" -> "_raft_log".
 */
public class RaftLog {
    private final DocumentStore store;
    private final String RAFT_DB = "_system";
    private final String RAFT_COL = "_raft_log";

    public RaftLog(DocumentStore store) throws Exception {
        this.store = store;
        this.store.createCollection(RAFT_DB, RAFT_COL);
    }

    public void append(LogEntry entry) throws Exception {
        Map<String, Object> doc = entry.toMap();
        // Use index as ID to ensure uniqueness and order
        doc.put("_id", String.valueOf(entry.getIndex()));
        store.save(RAFT_DB, RAFT_COL, doc);
    }
    
    public void deleteFrom(long index) throws Exception {
        // Delete all entries with index >= specified index
        // This is inefficient loop but simpler for file-based DB without range delete
        List<LogEntry> all = getAll();
        for (LogEntry entry : all) {
            if (entry.getIndex() >= index) {
                store.delete(RAFT_DB, RAFT_COL, String.valueOf(entry.getIndex()));
            }
        }
    }

    public LogEntry getEntry(long index) {
        try {
            Map<String, Object> doc = store.findByID(RAFT_DB, RAFT_COL, String.valueOf(index));
            if (doc == null) return null;
            return LogEntry.fromMap(doc);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public long getLastLogIndex() {
        try {
            // Optimization needed: maintain separate counter or read max ID
            List<LogEntry> all = getAll();
            if (all.isEmpty()) return 0;
            return all.get(all.size() - 1).getIndex();
        } catch (Exception e) {
            e.printStackTrace();
            return 0;
        }
    }

    public long getLastLogTerm() {
        LogEntry last = getEntry(getLastLogIndex());
        return last == null ? 0 : last.getTerm();
    }
    
    public List<LogEntry> getAll() throws Exception {
        List<Map<String, Object>> docs = store.query(RAFT_DB, RAFT_COL, null, 0, 0);
        List<LogEntry> entries = new ArrayList<>();
        for (Map<String, Object> doc : docs) {
            entries.add(LogEntry.fromMap(doc));
        }
        // Sort by index
        Collections.sort(entries, Comparator.comparingLong(LogEntry::getIndex));
        return entries;
    }

    public static class LogEntry {
        private long term;
        private long index;
        private String command; // JSON string of command
        private String type; // COMMAND, CONFIG, NOOP

        public LogEntry() {}

        public LogEntry(long term, long index, String command, String type) {
            this.term = term;
            this.index = index;
            this.command = command;
            this.type = type;
        }

        public Map<String, Object> toMap() {
            Map<String, Object> map = new ConcurrentHashMap<>();
            map.put("term", term);
            map.put("index", index);
            map.put("command", command);
            map.put("type", type);
            return map;
        }

        public static LogEntry fromMap(Map<String, Object> map) {
            LogEntry e = new LogEntry();
            e.term = ((Number) map.get("term")).longValue();
            e.index = ((Number) map.get("index")).longValue();
            e.command = (String) map.get("command");
            e.type = (String) map.get("type");
            return e;
        }

        public long getTerm() { return term; }
        public long getIndex() { return index; }
        public String getCommand() { return command; }
        public String getType() { return type; }
    }
}
