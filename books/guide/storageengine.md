# Storage Engines in JettraDB

JettraDB supports pluggable storage engines, allowing you to optimize database performance for specific use cases. Currently, two engines are available: **JettraBasicStore** and **JettraEngineStore**.

## Available Engines

### 1. JettraBasicStore (Default)

The `JettraBasicStore` is the default general-purpose storage engine. It prioritizes compatibility and human-readability.

*   **Format**: Documents are stored as individual files using standard serialization (mapped via Jackson). This effectively results in robust JSON/CBOR compatibility.
*   **Pros**:
    *   high compatibility.
    *   easier to debug (depending on serialization format).
    *   Standard directory structure.
*   **Cons**:
    *   Higher overhead for serialization/deserialization compared to optimized binary formats.
    *   Larger disk footprint for repetitive data keys.

### 2. JettraEngineStore

The `JettraEngineStore` is a high-performance engine optimized for Java-centric applications and speed.

*   **Format**: Uses `JettraBinarySerialization`, a custom binary format (`.jdb` files containing binary data).
*   **Pros**:
    *   **Performance**: Faster read/write operations due to optimized binary stream handling.
    *   **Efficiency**: Smaller file sizes for complex Java objects.
*   **Cons**:
    *   Less human-readable (requires Jettra tools to view content).
    *   Strictly coupled to Jettra binary serialization logic.

## Usage

### Web Interface

When creating a new database in the Web UI:

1.  Click **Create Database**.
2.  Enter the database name.
3.  Select the **Storage Engine** from the dropdown:
    *   `Basic Store`: Selects `JettraBasicStore`.
    *   `Engine Store`: Selects `JettraEngineStore`.

### Jettra Shell

To specify an engine when creating a database via the CLI shell:

```bash
# Create a standard database
create db my_basic_db

# Create a high-performance database
create db my_fast_db engine JettraEngineStore
```

### Curl / REST API

You can specify the `engine` parameter in the JSON body when creating a database:

```bash
curl -X POST http://localhost:8080/api/dbs \
  -H "Authorization: Basic <token>" \
  -H "Content-Type: application/json" \
  -d '{"name": "my_db", "engine": "JettraEngineStore"}'
```

The valid values for `engine` are:
*   `JettraBasicStore`
*   `JettraEngineStore`

### Java Driver

When implementing client-side logic, the engine is transparent to the driver for data operations (insert, find, etc.). However, if you are creating databases programmatically via a client wrapper, ensure your `createDatabase` method allows passing the engine parameter to the API as shown above.
