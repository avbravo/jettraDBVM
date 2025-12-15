package io.jettra.core.validation;

import java.util.List;
import java.util.Map;

import io.jettra.core.storage.DocumentStore;

public class ValidationManager implements Validator {

    private final DocumentStore store;

    public ValidationManager(DocumentStore store) {
        this.store = store;
    }

    @Override
    public void validate(String database, String collection, Map<String, Object> document) throws Exception {
        // Skip validation for system collections to prevent recursion and bootstrap issues
        if (collection.startsWith("_")) {
            return;
        }

        // Fetch rules
        // We query all documents in _rules. (Assuming there's only one or few)
        List<Map<String, Object>> rulesDocs = store.query(database, "_rules", null, 0, 0);
        if (rulesDocs == null || rulesDocs.isEmpty()) {
            return;
        }

        for (Map<String, Object> rulesDoc : rulesDocs) {
            if (rulesDoc.containsKey(collection)) {
                Object colRulesObj = rulesDoc.get(collection);
                if (colRulesObj instanceof List) {
                    List<?> fieldRules = (List<?>) colRulesObj;
                    for (Object obj : fieldRules) {
                        if (obj instanceof Map) {
                            @SuppressWarnings("unchecked")
                            Map<String, Object> fieldRuleWrapper = (Map<String, Object>) obj;
                            for (Map.Entry<String, Object> entry : fieldRuleWrapper.entrySet()) {
                                String fieldName = entry.getKey();
                                Object ruleDefObj = entry.getValue();
                                if (ruleDefObj instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> ruleDef = (Map<String, Object>) ruleDefObj;
                                    applyRule(database, collection, document, fieldName, ruleDef);
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private void applyRule(String db, String col, Map<String, Object> doc, String fieldName, Map<String, Object> rule) throws Exception {
        String type = (String) rule.get("type");
        Object val = doc.get(fieldName);
        Object ruleValue = rule.get("value");

        if (type == null) return;

        switch (type) {
            case "referenced":
                checkReferenced(db, val, rule);
                break;
            case "validation":
                if (ruleValue instanceof String) {
                    String v = (String) ruleValue;
                    if ("notnull".equalsIgnoreCase(v) || "not_null".equalsIgnoreCase(v)) {
                         if (val == null) throw new Exception("Validation Error: Field '" + fieldName + "' cannot be null");
                    } else if ("notempty".equalsIgnoreCase(v) || "not_empty".equalsIgnoreCase(v)) {
                        checkNotEmpty(fieldName, val);
                    } else if ("non_negative".equalsIgnoreCase(v)) {
                        checkNonNegative(fieldName, val);
                    }
                }
                break;
            case "not_null":
            case "notnull":
                 if (val == null) throw new Exception("Validation Error: Field '" + fieldName + "' cannot be null");
                 break;
            case "not_empty":
                checkNotEmpty(fieldName, val);
                break;
            case "non_negative":
                checkNonNegative(fieldName, val);
                break;
            case "min_value":
                checkMinValue(fieldName, val, ruleValue);
                break;
            case "max_value":
                checkMaxValue(fieldName, val, ruleValue);
                break;
            case "range":
                checkRange(fieldName, val, ruleValue);
                break;
            default:
                // Ignore unknown types
                break;
        }
    }

    private void checkNotEmpty(String field, Object val) throws Exception {
        if (val == null) return; // null is handled by not_null
        if (val instanceof String && ((String) val).isEmpty()) {
            throw new Exception("Validation Error: Field '" + field + "' cannot be empty");
        }
        if (val instanceof List && ((List) val).isEmpty()) {
            throw new Exception("Validation Error: Field '" + field + "' cannot be empty list");
        }
        if (val instanceof Map && ((Map) val).isEmpty()) {
            throw new Exception("Validation Error: Field '" + field + "' cannot be empty map");
        }
    }

    private void checkNonNegative(String field, Object val) throws Exception {
        if (val == null) return;
        if (val instanceof Number) {
            double d = ((Number) val).doubleValue();
            if (d < 0) {
                 throw new Exception("Validation Error: Field '" + field + "' cannot be negative");
            }
        }
    }

    private void checkMinValue(String field, Object val, Object min) throws Exception {
        if (val == null || min == null) return;
        if (val instanceof Number) {
            double dVal = ((Number) val).doubleValue();
            double dMin = Double.parseDouble(min.toString());
            if (dVal < dMin) {
                throw new Exception("Validation Error: Field '" + field + "' must be >= " + min);
            }
        }
    }

    private void checkMaxValue(String field, Object val, Object max) throws Exception {
         if (val == null || max == null) return;
        if (val instanceof Number) {
            double dVal = ((Number) val).doubleValue();
            double dMax = Double.parseDouble(max.toString());
            if (dVal > dMax) {
                throw new Exception("Validation Error: Field '" + field + "' must be <= " + max);
            }
        }
    }

    private void checkRange(String field, Object val, Object range) throws Exception {
        if (val == null || range == null) return;
        String[] parts = range.toString().split(",");
        if (parts.length != 2) return; 
        
        try {
            double min = Double.parseDouble(parts[0].trim());
            double max = Double.parseDouble(parts[1].trim());
            
            if (val instanceof Number) {
                double d = ((Number) val).doubleValue();
                if (d < min || d > max) {
                    throw new Exception("Validation Error: Field '" + field + "' must be between " + min + " and " + max);
                }
            }
        } catch (NumberFormatException e) {
            // Ignore format error in rule
        }
    }

    private void checkReferenced(String db, Object val, Map<String, Object> rule) throws Exception {
        if (val == null) return; // Allow null? usually referential integrity implies not null if field present.
        // But if field is optional, it can be null. If not null, must exist.
        
        String refCol = (String) rule.get("collectionreferenced");
        String extField = (String) rule.get("externalfield");
        
        if (refCol == null || extField == null) return;

        // Ensure val is the right type.
        // If externalfield is _id, val should be the ID string.
        // However, the user example logic:
        // "pais": {"_id": "..."} inside the document.
        // Wait.
        // in Document: "pais": {"_id": "6930f21cdf1d47955269c72c"}
        // The rule is on field "pais".
        // The value of "pais" is a MAP `{"_id": "..."}`.
        // So we need to extract `externalfield` from `val`.
        
        // OR, does the document contain just the ID?
        // User example:
        // persona [ { ..., "pais":{"_id": "6930f21cdf1d47955269c72c"} } ]
        // The value of persona.pais is a sub-document.
        // The rule says `externalfield: "_id"`.
        // This implies we look for `val.get("_id")`?
        // Or does it mean `pais` corresponds to `_id` in target collection?
        
        // Let's re-read:
        // "externalfield es el campo en la coleccion externa sobre la que se desea realizar la consulta"
        // (externalfield is the field in the external collection to query on)
        // AND "tengo un documento por ejemplo ... pais:{ "_id": "..." } ... en la coleccion persona usa una referencia"
        
        // This implies the value stored in `persona.pais` is `{"_id": "..."}`.
        // And we want to verify that `pais` collection has a document where `externalfield` (which is `_id`) matches `val.get("_id")`?
        // OR `val` itself (if it was a string).
        
        Object lookupValue = val;
        
        // If val is a Map, and we are looking for a referenced specific field value.
        // But usually foreign key is a simple value. 
        // In this specific user example, the foreign key seems to be embedded in an object `{"_id": "..."}`.
        // The user says: "puede observar que cuanto tiene un documento embebido que en su interior usa _id indica que es una referencia"
        
        // So if `val` is a Map and contains `_id`, we use that as the lookup value?
        // And we look it up against `externalfield` (`_id` usually) in `collectionreferenced`.
        
        if (val instanceof Map) {
             Map<?,?> valMap = (Map<?,?>) val;
             // If the rule points to `_id`, we try to get `_id` from the map object if present?
             // Or maybe `externalfield` implies what key to look for in the embedded object AND the target object?
             
             // User says: "externalfield es el campo en la coleccion externa"
             // Typically if I have `pais: { _id: "123" }`, I want to find a document in `pais` collection where `_id` == "123".
             // So I extract "_id" from the embedded object?
             // What if `externalfield` was "code"? `pais: { code: "PA" }` -> find in pais where code == "PA".
             // So we should try to extract `externalfield` from the value map.
             
             if (valMap.containsKey(extField)) {
                 lookupValue = valMap.get(extField);
             } else {
                 // Fallback or error?
                 // If the value is a map but doesn't contain the key we are looking for reference...
                 // Maybe the Reference IS the object, but we match specifically on externalfield.
                 // Let's assume lookupValue is valMap.get(extField)
                 // If null, maybe validation fails?
                 if (lookupValue == null) {
                      // If referenced field is missing in the object, can't validate reference.
                      // Depending on strictness.
                      return; 
                 }
             }
        }
        
        // Now perform lookup
        if ("_id".equals(extField)) {
             // Use findByID optimization
             Map<String, Object> found = store.findByID(db, refCol, String.valueOf(lookupValue));
             if (found == null) {
                  throw new Exception("Referential Integrity Error: " + refCol + " with " + extField + "=" + lookupValue + " does not exist.");
             }
        } else {
             // Use query
             Map<String, Object> filter = java.util.Collections.singletonMap(extField, lookupValue);
             List<Map<String, Object>> found = store.query(db, refCol, filter, 1, 0);
             if (found.isEmpty()) {
                  throw new Exception("Referential Integrity Error: " + refCol + " with " + extField + "=" + lookupValue + " does not exist.");
             }
        }
    }
    @Override
    public void validateDelete(String database, String collection, Map<String, Object> document) throws Exception {
        // Skip validation for system collections
        if (collection.startsWith("_")) {
            return;
        }

        // Fetch all rules to check for reverse references
        List<Map<String, Object>> rulesDocs = store.query(database, "_rules", null, 0, 0);
        if (rulesDocs == null || rulesDocs.isEmpty()) {
            return;
        }

        for (Map<String, Object> rulesDoc : rulesDocs) {
             // rulesDoc is like { "collectionName": [ rules... ] }
             // We iterate over all keys (collections)
             for (String sourceCol : rulesDoc.keySet()) {
                 if (sourceCol.equals("_id")) continue; // Skip ID

                 Object colRulesObj = rulesDoc.get(sourceCol);
                 if (colRulesObj instanceof List) {
                     List<?> fieldRules = (List<?>) colRulesObj;
                     for (Object obj : fieldRules) {
                         if (obj instanceof Map) {
                             @SuppressWarnings("unchecked")
                             Map<String, Object> fieldRuleWrapper = (Map<String, Object>) obj;
                             for (Map.Entry<String, Object> entry : fieldRuleWrapper.entrySet()) {
                                 String fieldName = entry.getKey();
                                 Object ruleDefObj = entry.getValue();
                                 if (ruleDefObj instanceof Map) {
                                     @SuppressWarnings("unchecked")
                                     Map<String, Object> ruleDef = (Map<String, Object>) ruleDefObj;
                                     String type = (String) ruleDef.get("type");
                                     
                                     // Check if this rule is a reference to the collection we are deleting from
                                     if ("referenced".equals(type)) {
                                         String refCol = (String) ruleDef.get("collectionreferenced");
                                         if (collection.equals(refCol)) {
                                             // Found a reference pointing to us!
                                             checkReverseReference(database, collection, sourceCol, fieldName, ruleDef, document);
                                         }
                                     }
                                 }
                             }
                         }
                     }
                 }
             }
        }
    }

    private void checkReverseReference(String db, String targetCollection, String sourceCol, String sourceField, Map<String, Object> rule, Map<String, Object> documentToDelete) throws Exception {
         String extField = (String) rule.get("externalfield"); // Field in documentToDelete (e.g. _id)
         if (extField == null) return;
         
         Object valInDeletedDoc = documentToDelete.get(extField);
         if (valInDeletedDoc == null) return; // Can't block if value is null (unless logic dictates otherwise?)

         // We need to check if sourceCol has any document where sourceField matches valInDeletedDoc
         // But sourceField in sourceCol might be embedded (e.g. pais: { _id: ... }) logic again.
         // If Validation logic for `referenced` assumes `val` in source doc is the lookup key (or embedded map),
         // we need to query `sourceCol` for `sourceField` matching `valInDeletedDoc`.
         
         // If the stored value in sourceCol is a primitive matching `valInDeletedDoc`:
         // Query: { "sourceField": valInDeletedDoc }
         
         // If the stored value is a Map { "_id": ... } and we are matching on ID?
         // The rule definition is slightly ambiguous on HOW the data is stored in Source vs Target.
         // Based on `checkReferenced`, `val` in Source can be Map.
         // If `extField` is `_id` (common case), and Source stores `{ _id: "..." }`.
         // Then we should query `sourceField._id` = `valInDeletedDoc`.
         // OR if Source stores just "..." (id), we query `sourceField` = `valInDeletedDoc`.
         
         // Let's assume standard Jettra referencing style:
         // If `extField` is `_id`, we usually store the ID or an object containing the ID.
         // Let's try to act conservatively: if we find ANY match, we block.
         
         // Case 1: Simple match
         Map<String, Object> filter1 = new java.util.HashMap<>();
         filter1.put(sourceField, valInDeletedDoc);
         List<Map<String, Object>> found1 = store.query(db, sourceCol, filter1, 1, 0);
         if (!found1.isEmpty()) {
             throw new Exception("Referential Integrity Violation: Cannot delete document in '" + targetCollection + "' because it is referenced by '" + sourceCol + "'");
         }
         
         // Case 2: Nested match (if valInDeletedDoc is an ID and source stores objects with that ID)
         // E.g. sourceField.extField == valInDeletedDoc
         // BUT Jettra query engine might not support dot notation filters? 
         // Assuming it doesn't support dot notation based on `FilePersistence` implementation (simple iteration loop).
         // Wait, `FilePersistence.query` checks: `entry.getValue().equals(docMap.get(entry.getKey()))`.
         // It uses `docMap.get(key)`. So it only supports top-level keys.
         // If reference is stored as object `pais: { _id: ... }`, we can't query `pais._id`.
         
         // This is a LIMITATION of the current store.
         // To support this, we would need to manually scan or update Store to support dot notation.
         // Given I cannot easily rewrite Store query engine completely right now, I will assume references are stored as simple values OR I must implement a manual scan here (inefficient but safe).
         
         // Manual Scan for complex objects if Filter 1 didn't catch it
         // Only if we suspect object storage?
         // Let's safe-guard: scan source collection and check manually.
         // Yes, iteration is expensive but necessary for integrity if query engine is weak. (And this is a persistent file store, so not huge scale yet).
         
         List<Map<String, Object>> allSource = store.query(db, sourceCol, null, 0, 0); // null filter = all
         for (Map<String, Object> srcDoc : allSource) {
             Object srcVal = srcDoc.get(sourceField);
             if (srcVal == null) continue;
             
             Object effectiveVal = srcVal;
             if (srcVal instanceof Map && ((Map<?,?>)srcVal).containsKey(extField)) {
                 effectiveVal = ((Map<?,?>)srcVal).get(extField);
             }
             
             if (valInDeletedDoc.equals(effectiveVal)) {
                 throw new Exception("Referential Integrity Violation: Cannot delete document from '" + 
                     targetCollection
                     + "' because it is referenced by document " + srcDoc.get("_id") + " in '" + sourceCol + "'");
             }
         }
    }
}
