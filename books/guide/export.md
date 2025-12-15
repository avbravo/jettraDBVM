# Export Data

JettraDB provides versatile options for exporting your data from collections in JSON or CSV formats.

## Web Interface

1. Navigate to the **Import / Export** section in the sidebar.
2. In the "Export Collection" card:
   - Select the **Database**.
   - Select the **Collection**.
   - Choose the **Format** (JSON or CSV).
3. Click **Download Export File**.

The file will be downloaded to your computer with the naming convention `collection_name.format`.

## Jettra Shell

You can export data directly from the shell using the `export` command.

```bash
jettra> use my_db
jettra:my_db> export my_collection json output.json
Export successful: output.json
```

**Syntax:**
```bash
export <collection> <format> <filename>
```
- `collection`: Name of the collection to export.
- `format`: `json` or `csv`.
- `filename`: Local path where the file will be saved.

## cURL

You can use the HTTP API to export data.

```bash
curl -u user:password "http://localhost:8080/api/export?db=my_db&col=my_collection&format=json" > output.json
```

**Parameters:**
- `db`: Database name.
- `col`: Collection name.
- `format`: `json` or `csv`.

## Java Driver

Using the `JettraClient`, you can export data programmatically.

```java
JettraClient client = new JettraClient("localhost", 8080, "admin", "admin");

// Export to JSON
client.exportCollection("my_db", "users", "json", Paths.get("users.json"));

// Export to CSV
client.exportCollection("my_db", "users", "csv", Paths.get("users.csv"));
```
