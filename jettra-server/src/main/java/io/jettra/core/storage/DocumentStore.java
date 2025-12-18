package io.jettra.core.storage;

import java.util.List;
import java.util.Map;

public interface DocumentStore {
    String save(String database, String collection, Map<String, Object> document) throws Exception;

    Map<String, Object> findByID(String database, String collection, String id) throws Exception;

    List<Map<String, Object>> query(String database, String collection, Map<String, Object> filter, int limit,
            int offset)
            throws Exception;

    void update(String database, String collection, String id, Map<String, Object> document) throws Exception;

    void delete(String database, String collection, String id) throws Exception;

    int count(String database, String collection) throws Exception;

    Map<String, List<String>> getDatabaseStructure() throws Exception;

    void renameDatabase(String oldName, String newName) throws Exception;

    void createDatabase(String name, String engine) throws Exception;

    void deleteDatabase(String name) throws Exception;

    String backupDatabase(String database) throws Exception;

    void restoreDatabase(String zipFilename, String targetDatabase) throws Exception;

    // Versioning
    List<String> getVersions(String database, String collection, String id) throws Exception;

    void restoreVersion(String database, String collection, String id, String version) throws Exception;

    Map<String, Object> getVersionContent(String database, String collection, String id, String version)
            throws Exception;

    // Metadata/Engine Info
    String getDatabaseEngine(String database) throws Exception;

    default void reload() throws Exception {
    }

    long getNextSequence(String database, String collection, String field) throws Exception;

    void createCollection(String database, String collection) throws Exception;

    void renameCollection(String database, String oldName, String newName) throws Exception;

    void deleteCollection(String database, String collection) throws Exception;

    // Transactions
    String beginTransaction() throws Exception;

    void commitTransaction(String txID) throws Exception;

    void rollbackTransaction(String txID) throws Exception;

    void saveTx(String database, String collection, Map<String, Object> document, String txID) throws Exception;

    void deleteTx(String database, String collection, String id, String txID) throws Exception;
}
