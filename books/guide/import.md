# Import Data

JettraDB allows you to import data into collections from JSON or CSV files.

## Web Interface

1. Navigate to the **Import / Export** section in the sidebar.
2. In the "Import Data" card:
   - Select the target **Database**.
   - Select an existing **Collection** OR enter a **New Collection Name**.
   - Choose the **Format** of the file (JSON or CSV).
   - Click **Browse** to select the file from your computer.
3. Click **Import Data**.

You will receive a notification confirming the number of documents imported.

## Jettra Shell

You can import data directly from the shell using the `import` command.

```bash
jettra> use my_db
jettra:my_db> import my_collection json data.json
Import successful!
```

**Syntax:**
```bash
import <collection> <format> <filename>
```
- `collection`: Target collection name.
- `format`: `json` or `csv`.
- `filename`: Local path of the file to import.

## cURL

You can use the HTTP API to import data by uploading a file.

```bash
curl -u user:password -X POST \
  -H "Content-Type: application/octet-stream" \
  --data-binary @data.json \
  "http://localhost:8080/api/import?db=my_db&col=my_collection&format=json"
```

**Parameters:**
- `db`: Database name.
- `col`: Collection name.
- `format`: `json` or `csv`.

## Java Driver

Using the `JettraClient`, you can import data programmatically.

```java
JettraClient client = new JettraClient("localhost", 8080, "admin", "admin");

// Import from JSON
client.importCollection("my_db", "users", "json", Paths.get("data.json"));

// Import from CSV
client.importCollection("my_db", "users", "csv", Paths.get("data.csv"));
```

### File Formats

**JSON:**
Must be a JSON Array of objects.
```json
[
  {"name": "Alice", "age": 30},
  {"name": "Bob", "age": 25}
]
```

**CSV:**
Must have a header row.
```csv
name,age
Alice,30
Bob,25
```
