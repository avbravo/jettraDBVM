package io.jettra.core.storage;

import java.util.List;
import java.util.Map;

public interface IndexEngine {
    void createIndex(String database, String collection, List<String> fields, boolean unique, boolean sequential)
            throws Exception;

    void updateIndex(String database, String collection, String id, Map<String, Object> document) throws Exception;

    List<String> findWithIndex(String database, String collection, List<String> fields, List<String> values)
            throws Exception;

    List<IndexDefinition> getIndexes(String database, String collection) throws Exception;

    void deleteIndex(String database, String collection, List<String> fields) throws Exception;

    void reload() throws Exception;

    record IndexDefinition(List<String> fields, boolean unique, boolean sequential) {
    }
}
