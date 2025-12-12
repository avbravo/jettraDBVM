package io.jettra.core.auth;

import io.jettra.core.storage.DocumentStore;
import java.util.Map;
import java.util.List;
import java.util.HashMap;

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

    public boolean authorize(String db, String username, String requiredRole) {
        try {
            // Get user role
            String role = getUserRole(db, username);
            if (role == null) {
                // Try global system db
                role = getUserRole("_system", username);
            }
            if (role == null)
                return false;

            if (role.equals("admin"))
                return true; // Superuser

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

    private String getUserRole(String db, String username) throws Exception {
        Map<String, Object> filter = new HashMap<>();
        filter.put("username", username);
        List<Map<String, Object>> users = store.query(db, "_users", filter, 1, 0);
        if (users.isEmpty())
            return null;
        return (String) users.get(0).get("role");
    }
}
