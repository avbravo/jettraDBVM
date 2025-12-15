# Jettra Versioning Guide

## Concept
JettraDB employs an **LSM-style (Log-Structured Merge-tree) append-only versioning system**. When a document is updated or deleted, the previous version is not overwritten but instead archived with a timestamp. This allows for:
- Historical auditing of data changes.
- Recovery from accidental deletions or incorrect updates.
- Point-in-time queries (future feature).

Versions are stored in a hidden `_versions` subdirectory within each collection.

## REST API

### List Versions
Retrieve a list of available versions for a specific document.

**Request:**
`GET /api/versions?db=<database>&col=<collection>&id=<document_id>`

**Response:**
A JSON array of timestamp strings (e.g., `["1798362512344", "1798362510000"]`), ordered from newest to oldest.

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

## Usage Scenarios

### Auditing
To see how a document has changed over time, you can list its versions and retrieve the content of specific historical versions (note: direct content retrieval of old versions via API is planned, currently you restore to view).

### Accident Recovery
If a user accidentally updates a user profile with incorrect data:
1. List versions to find the timestamp before the error.
2. Call `restore-version` with that timestamp.
3. The profile is reverted, and the erroneous state is saved as history.
