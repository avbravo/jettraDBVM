package io.jettra.core.auth;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.jettra.core.storage.DocumentStore;

public class AuthManager {
    private final DocumentStore store;

    public AuthManager(DocumentStore store) {
        this.store = store;
    }

    public boolean authenticate(String db, String username, String password) {
        try {
            // 1. Check if it's the super admin in '_system' DB
            if (checkUser("_system", username, password))
                return true;

            // 2. Check in the specific DB
            return checkUser(db, username, password);
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    private boolean checkUser(String db, String username, String password) throws Exception {
        Map<String, Object> filter = new HashMap<>();
        filter.put("username", username);
        List<Map<String, Object>> users = store.query(db, "_users", filter, 1, 0);
        if (users.isEmpty())
            return false;

        Map<String, Object> user = users.get(0);
        String storedPass = (String) user.get("password");
        return storedPass.equals(password);
    }

    private Map<String, Object> getUser(String db, String username) throws Exception {
        Map<String, Object> filter = new HashMap<>();
        filter.put("username", username);
        List<Map<String, Object>> users = store.query(db, "_users", filter, 1, 0);
        if (users.isEmpty()) return null;
        return users.get(0);
    }

    public boolean authorize(String db, String username, String requiredRole) {
        try {
            // Get user role
            String role = getUserRole(db, username);
            Map<String, Object> user = getUser(db, username);

            if (role == null) {
                // Try global system db
                role = getUserRole("_system", username);
                user = getUser("_system", username);
            }
            if (role == null) return false;

            if (role.equals("admin")) return true; // Superuser

            // DB Access Check (if user is from _system but not admin)
            if (user != null && "_system".equals(db) == false) { // If we are accessing a specific DB (not _system itself)
                 // Check if user has explicit access list
                 Object allowedObj = user.get("allowed_dbs");
                 if (allowedObj instanceof List) {
                     List<String> allowed = (List<String>) allowedObj;
                     if (!allowed.contains(db) && !allowed.contains("*")) {
                         return false; // Deny if not in list
                     }
                 }
                 // If allowed_dbs is missing, do we allow all? 
                 // Current security model: if you are in _system you are global unless restricted? 
                 // Or we default to deny? The prompt implies we want to RESTRICT.
                 // Let's assume if 'allowed_dbs' exists, we enforce it. If not, we assume legacy global (allow).
            }

            // owner > writereader > reader

            // owner > writereader > reader
            if (requiredRole.equals("reader")) {
                return role.equals("owner") || role.equals("writereader") || role.equals("reader");
            }
            if (requiredRole.equals("writereader")) {
                return role.equals("owner") || role.equals("writereader");
            }
            if (requiredRole.equals("owner")) {
                return role.equals("owner");
            }

            return false;
        } catch (Exception e) {
            return false;
        }
    }

    public String getUserRole(String db, String username) throws Exception {
        Map<String, Object> filter = new HashMap<>();
        filter.put("username", username);
        List<Map<String, Object>> users = store.query(db, "_users", filter, 1, 0);
        if (users.isEmpty())
            return null;
        return (String) users.get(0).get("role");
    }
}
