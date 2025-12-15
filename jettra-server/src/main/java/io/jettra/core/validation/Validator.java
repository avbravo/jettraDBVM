package io.jettra.core.validation;

import java.util.Map;

public interface Validator {
    void validate(String database, String collection, Map<String, Object> document) throws Exception;
    void validateDelete(String database, String collection, Map<String, Object> document) throws Exception;
}
