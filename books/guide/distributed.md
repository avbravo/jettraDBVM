# Distributed JettraDBVM with Raft

JettraDBVM supports distributed deployment using the Raft consensus algorithm for fault tolerance and high availability.

## Architecture

The system uses a Leader-Follower model:
- **Leader**: Handles all write operations and replicates entries to followers.
- **Followers**: Replicate state from the leader.
- **Candidate**: A state assumed during leader election.

Nodes communicate via HTTP JSON-RPC at `/raft/rpc/*`.

## Configuration

To enable distributed mode, update `config.json` in your data directory:

```json
{
  "Host": "0.0.0.0",
  "Port": 8080,
  "DataDir": "data",
  "distributed": true,
  "NodeID": "node1",
  "Bootstrap": true,
  "Peers": []
}
```

### Parameters
### Parameters
- **distributed**: Set to `true` to enable Raft.
- **NodeID**: Unique identifier for this node (string).
- **Bootstrap**: Set to `true` on the **first** node to be started (the initial leader). Other nodes should have this set to `false`.
- **Peers**: List of URLs of other nodes. This is only used for initial discovery. **The active cluster configuration is stored in the `_system._clusternodes` collection in the database.**

## Cluster Configuration

JettraDBVM stores cluster membership in the `_system._clusternodes` collection. This system collection is automatically replicated to all nodes.

It contains documents with:
- **_id**: Node Identifier (e.g. `node-12345`).
- **url**: Node's HTTP address.
- **role**: Current role (LEADER, CANDIDATE, FOLLOWER).
- **status**: Node status (ACTIVE, INACTIVE).

**Important Rules:**
1. **Leader Control**: Only the Leader can process membership changes (Add/Remove/Stop).
2. **Replication**: The Leader sends commands via Raft entries (`cluster_register`, `cluster_stop`, etc.) which are applied to the local database on all nodes.
3. **Failover**: If the Leader fails, a new election occurs. Nodes use their local `_clusternodes` collection to know valid peers.
4. **Followers**: Followers update their peer list based on changes to the `_clusternodes` collection.

## Dynamic Peer Management

Use the Leader's API or tools to manage peers.

### Adding a New Node

1. **Start the new node** (e.g., Node 2) with `distributed: true`.
2. **Register the new node** by sending a request to the **Leader** (e.g., Node 1).

**API Endpoint:** `POST /api/cluster/register` (must be sent to Leader)

**Request Body:**
```json
{
  "url": "http://node2_host:8081"
}
```

**Behavior:**
- The Leader adds a document for Node 2 in `_system._clusternodes`.
- The 'cluster_register' command is replicated to all nodes.
- Node 2 receives the snapshot/logs and updates its internal peer list.

### Example: Setting up a 3-Node Cluster

1. **Start Node 1** (Leader Candidate):
   Config: `{ "Port": 8080, "Bootstrap": true, "distributed": true, "NodeID": "node1", "Peers": [] }`
   *Result:* Node 1 initializes `_clusternodes` with itself as Leader.
   
2. **Start Node 2**:
   Config: `{ "Port": 8081, "Bootstrap": false, "distributed": true, "NodeID": "node2", "Peers": [] }`

3. **Register Node 2 on Node 1**:
   ```bash
   curl -X POST http://localhost:8080/api/cluster/register -d '{"url": "http://localhost:8081"}'
   ```
   *Result:* `_clusternodes` on Node 1 is updated and replicated to Node 2.

4. **Start Node 3**:
   Config: `{ "Port": 8082, "Bootstrap": false, "distributed": true, "NodeID": "node3", "Peers": [] }`

5. **Register Node 3 on Node 1**:
   ```bash
   curl -X POST http://localhost:8080/api/cluster/register -d '{"url": "http://localhost:8082"}'
   ```
   *Result:* `_clusternodes` now contains 3 nodes.

### Removing a Node

To remove a node from the cluster:

**API Endpoint:** `POST /api/cluster/deregister` (must be sent to Leader)

**Request Body:**
```json
{
  "url": "http://node2_host:8081"
}
```

**Behavior:**
- The Leader removes the node's document from `_clusternodes`.
- The 'cluster_deregister' command is replicated.

### Stopping a Node

To stop a node gracefully:

**API Endpoint:** `POST /api/cluster/stop`

**Request Body:**
```json
{
  "node": "http://node2_host:8081"
}
```
*Can accept URL or Node ID.*

**Behavior:**
- Sends a stop signal to the target node.
- Marks the node as `INACTIVE` in `_clusternodes`.


## Client Tools Management

You can manage the cluster using the JettraDB Shell and Java Driver.

### JettraDB Shell

Start the shell and connect to any node:

```bash
$ ./jettra-shell/target/jettraDBVMShell.jar
jettra> connect http://localhost:8080
Connected to http://localhost:8080
```

**Commands:**

- **Check Status**:
  ```bash
  jettra> federated nodes
  ```

### Java Driver

The `JettraClient` provides methods for programmatic management:

```java
JettraClient client = new JettraClient("localhost", 8080, "admin", "password");

```java
// Get Status
List<Map<String, Object>> nodes = client.getFederatedNodes();
```
```

## Management UI

Access the **Cluster** section in the Web UI (`http://localhost:8080`) to view:
- Current Node State (Leader/Follower)
- Current Term
- Cluster Peers
