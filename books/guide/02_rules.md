# Validation Rules

JettraDB allows you to define validation rules for your documents. These rules are stored in a special collection called `_rules` within each database.

## Structure of `_rules`

The `_rules` collection contains documents that define the schema and validation logic for other collections.

Example:

```json
{
  "persona": [
    {
      "pais": {
        "type": "referenced",
        "collectionreferenced": "pais",
        "externalfield": "_id"
      }
    },
    {
      "nombre": {
        "type": "validation",
        "value": "notnull"
      }
    },
    {
      "edad": {
        "type": "min_value",
        "value": 18
      }
    }
  ]
}
```

## Supported Rules

| Type | Value | Description |
|---|---|---|
| `version` | `notnull`, `not_null` | Field cannot be null. |
| `validation` | `notempty`, `not_empty` | String/List/Map cannot be empty. |
| `validation` | `non_negative` | Number must be >= 0. |
| `min_value` | (number) | Number must be >= value. |
| `max_value` | (number) | Number must be <= value. |
| `range` | "min,max" | Number must be between min and max. |
| `referenced` | (N/A) | Enforces referential integrity. Requires `collectionreferenced` and `externalfield`. **Note:** This also blocks deletion of referenced documents. |

## Examples

### 1. via cURL

**Define Rules:**

```bash
curl -u user:pass -X POST "http://localhost:8080/api/doc?db=mydb&col=_rules" -d '{
  "products": [
    {
      "price": { "type": "min_value", "value": 0 }
    },
    {
      "category": {
         "type": "referenced",
         "collectionreferenced": "categories",
         "externalfield": "code"
      }
    }
  ]
}'
```

**Insert Valid Document:**

```bash
curl -u user:pass -X POST "http://localhost:8080/api/doc?db=mydb&col=products" -d '{
  "name": "Laptop",
  "price": 1200,
  "category": { "code": "ELEC" }
}'
```

### 2. via Shell

```bash
use mydb
insert into _rules {
  "users": [
    { "email": { "type": "validation", "value": "notnull" } }
  ]
}
```

### 3. via Java Driver

```java
import io.jettra.driver.JettraClient;
import java.util.Map;
import java.util.List;

JettraClient client = new JettraClient("http://localhost:8080", "admin", "adminadmin");

// Define Rule
Map<String, Object> emailRule = Map.of(
    "email", Map.of("type", "validation", "value", "not_null")
);
Map<String, Object> rulesDoc = Map.of("users", List.of(emailRule));

client.insert("mydb", "_rules", rulesDoc);

// Insert Document
try {
    client.insert("mydb", "users", Map.of("email", "john@example.com"));
    System.out.println("User inserted");
} catch (Exception e) {
    System.err.println("Validation failed: " + e.getMessage());
}
```
