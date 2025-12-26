# Jettra Memory DB Guide

## Overview

`jettra-memory` is a high-performance, in-memory database module designed to be part of the JettraDB ecosystem. It is optimized for speed and efficiency, operating entirely in RAM without disk persistence for data operations, making it ideal for transient data, caching, or ultra-low latency requirements.

## Key Features

1.  **In-Memory Architecture**: All data resides in RAM. No disk I/O latency for read/write operations.
2.  **Resource Optimization**: Implements algorithms to manage memory consumption efficiently using custom resource monitors.
3.  **LSM-Tree Optimization**: Uses Log-Structured Merge-tree inspired structures adapted for memory to handle high write throughput and efficient compaction.
4.  **ACID Transactions**: Supports atomicity, consistency, isolation, and durability (within the lifespan of the process) using MVCC (Multi-Version Concurrency Control).
5.  **Referential Integrity & Rules**: Supports defining relationships and rules similar to `jettra-server`.
6.  **Indexing**: Efficient memory-based indexing for fast lookups.

## Project Structure

- **jettra-memory**: The core database engine library.
- **jettra-memory-driver**: A client driver/API to interact with the database engine.
- **jettra-memory-shell**: A command-line interface for interactive management and querying.

## Installation & Setup

Ensure you have the `jettraDBVM` project checked out.

1.  Build the entire project:
    ```bash
    mvn clean install
    ```

2.  Run the Shell:
    The shell allows you to interact with an embedded instance.
    ```bash
    java -jar jettra-memory-shell/target/jettra-memory-shell-1.0-SNAPSHOT.jar
    ```

## Usage Examples

### Using the Driver (Java)

```java
import io.jettra.memory.driver.JettraMemoryDriver;
import io.jettra.memory.JettraMemoryDB;

public class Example {
    public static void main(String[] args) {
        JettraMemoryDriver driver = new JettraMemoryDriver();
        driver.connect("my_memory_db");
        
        JettraMemoryDB db = driver.getDB();
        // Operations...
        
        driver.close();
    }
}
```

### Using the Shell

Start the shell:
```bash
$ ./jettra-memory-shell
```

**Commands:**

*   `status`: Check the status of the database and resource usage.
*   `create collection <name>`: Create a new collection.
*   `insert <collection> <json>`: Insert a document.
*   `find <collection> <query>`: Query documents.

*(Note: Shell commands are currently in active development)*

## Configuration

Jettra Memory uses a JSON configuration file, typically `memory.json`.

**Example `memory.json`:**
```json
{
    "server": {
        "port": 9090,
        "host": "0.0.0.0"
    },
    "memory": {
        "maxWait": 500,
        "critical_threshold": 0.85,
        "compression": false,
        "mem_table_size": 1048576,
        "max_memory_bytes": 0
    },
    "security": {
        "admin_password": "adminadmin"
    }
}
```

## Jettra Memory Server & Web UI

The project now includes a standalone server mode (`JettraMemoryServer`) which provides:
1.  **REST API**: For monitoring status and management.
2.  **Web Dashboard**: Accessed via `http://<host>:<port>/`, allowing visualization of memory usage, collection stats, and configuration.
    *   **Dashboard**: Real-time memory and uptime stats.
    *   **Collections**: View and manage active collections.
    *   **Config**: Update admin password and other settings.

To start the server:
```bash
java -cp jettra-memory/target/jettra-memory-1.0-SNAPSHOT.jar io.jettra.memory.JettraMemoryServer
```

## Federated Integration Strategy

The Jettra Federated Server (`jettra-federated`) has been enhanced to orchestrate In-Memory databases alongside persistent nodes.

### 1. Memory Leader Election
*   The federated network maintains a registry of active `jettra-memory` nodes.
*   **Leader Selection Logic**: The system prioritizes the memory node effectively "closest" to the Federated Leader (e.g., localhost or lowest latency).
*   **Failover**: If the Memory Leader becomes unresponsive, a new one is immediately elected from the active pool.

### 2. High-Performance "Write-Behind" CRUD
To achieve maximum throughput, the Federated Server implements a tiered data operation model:

1.  **Sync Write to Memory**: The operation (Create/Update/Delete) is first executed against the **Memory Leader**. This is a fast, in-memory operation ensuring low latency for the client.
2.  **Async Persistence**: Upon success in memory, the operation is asynchronously forwarded to the **Persistent Data Cluster Leader** (Jettra Server).
    *   This decouples client latency from disk I/O.
    *   The persistent leader then replicates the data to follower nodes as usual.

### 3. Federated Management
The Federated Server manages memory nodes similar to DB nodes:
*   **Registration**: Memory nodes register themselves via the federated API.
*   **Health Checks**: Periodic heartbeats ensure nodes are active.
*   **Status**: You can view the status of memory nodes and the current Memory Leader via the Federated API `/federated/status`.

## Security

*   **Default Admin**: Username `admin`, Password `adminadmin`.
*   **Password Change**: Administrators can update their password via the Web UI or API.
*   **API Security**: Critical management endpoints are protected.

