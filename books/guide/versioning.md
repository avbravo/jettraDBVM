# Jettra Versioning Guide

## Concept
JettraDB employs an **LSM-style (Log-Structured Merge-tree) append-only versioning system**. When a document is updated or deleted, the previous version is not overwritten but instead archived with a timestamp. This allows for:
- Historical auditing of data changes.
- Recovery from accidental deletions or incorrect updates.
- Point-in-time queries.

Versions are stored in a hidden `_versions` subdirectory within each collection.

## REST API

### List Versions
Retrieve a list of available versions for a specific document.

**Request:**
`GET /api/versions?db=<database>&col=<collection>&id=<document_id>`

**Response:**
A JSON array of timestamp strings (e.g., `["1798362512344", "1798362510000"]`), ordered from newest to oldest.

### Get Version Content
Retrieve the content of a specific version without restoring it.

**Request:**
`GET /api/version?db=<database>&col=<collection>&id=<document_id>&version=<timestamp>`

**Response:**
The JSON document as it existed at that timestamp.

### Restore Version
Restore a document to a specific previous version. The current state of the document at the time of restoration is itself saved as a new version before being overwritten, ensuring no data is ever lost.

**Request:**
`POST /api/restore-version`

**Body:**
```json
{
  "db": "my_database",
  "col": "my_collection",
  "id": "doc_id",
  "version": "1798362512344"
}
```

## CLI Shell Usage

### Viewing History
Use the `history` command to list available versions for a document.

Identifique el _id del documento a procesar 

Por ejemplo

select * from mycollection 

se genera la lista y alli identifique el _id y lo reemplaza


```bash
jettra:mydb> history mycollection _id
Versions:
["1734282300000", "1734282200000"]
```

### Viewing Version Content
Use the `show version` command to inspect the data of a specific version.

```bash
jettra:mydb> show version mycollection _id 1734282300000
{
  "_id": "doc1",
  "name": "Old Name",
  "status": "pending"
}
```

### Restoring a Version
Use the `revert` command to restore the document to a previous state.

```bash
jettra:mydb> revert mycollection _id 1734282300000
Version restored.
```

## Java Driver Usage

The `JettraClient` provides methods to interact with the versioning system programmatically.

```java
JettraClient client = new JettraClient("localhost", 8080, "admin", "password");

// 1. List valid versions
List<String> versions = client.getVersions("mydb", "mycollection", "doc1");
System.out.println("Available versions: " + versions);

if (!versions.isEmpty()) {
    String lastVersion = versions.get(0);

    // 2. Inspect content of the last version
    Map<String, Object> content = client.getVersionContent("mydb", "mycollection", "doc1", lastVersion);
    System.out.println("Snapshot content: " + content);

    // 3. Restore to that version
    client.restoreVersion("mydb", "mycollection", "doc1", lastVersion);
    System.out.println("Restoration complete.");
}
```

## cURL Examples

```bash
# List versions
curl -u admin:password "http://localhost:8080/api/versions?db=mydb&col=mycol&id=doc1"

# View content
curl -u admin:password "http://localhost:8080/api/version?db=mydb&col=mycol&id=doc1&version=1734282300000"

# Restore
curl -u admin:password -X POST http://localhost:8080/api/restore-version \
  -H "Content-Type: application/json" \
  -d '{"db":"mydb", "col":"mycol", "id":"doc1", "version":"1734282300000"}'
```
