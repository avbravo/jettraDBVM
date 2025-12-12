# JettraDB Distributed Deployment Guide

This guide describes how to deploy JettraDB in a Raft-based distributed cluster.

## Architecture
JettraDB uses the Raft consensus algorithm for leader election and log replication.
- **Leader**: Handles all write operations.
- **Followers**: Replicate data from the leader.
- **Quorum**: A majority (N/2 + 1) is required for improved availability and consistency.

## Configuration
Edit `config.json` for each node:
```json
{
  "Host": "0.0.0.0",
  "Port": 8080,
  "DataDir": "data",
  "Bootstrap": true, 
  "ClusterID": "cluster-1",
  "NodeID": "node-1"
}
```
- **Bootstrap**: Set to `true` ONLY for the first node (init leader).
- **NodeID**: Must be unique per node.
- **Peers**: No longer configured here. Managed dynamically via UI/API and duplicated to `_system/_cluster`.

## Deployment Options

### 1. Native
Run multiple instances on different ports/servers.
```bash
# Node 1
java -jar target/jettraDBVM-1.0-SNAPSHOT.jar
# Node 2 (ensure config.json keys correspond to unique paths/ports)
java -jar target/jettraDBVM-1.0-SNAPSHOT.jar
```

### 2. Docker
Build the image:
```bash
docker build -t jettradb .
```
Run container:
```bash
docker run -d -p 8080:8080 -v $(pwd)/data:/app/data --name node1 jettradb
```

### 3. Kubernetes
Apply the StatefulSet:
```bash
kubectl apply -f kubernetes.yaml
```
The StatefulSet creates 3 replicas accessible via `jettradb-0.jettradb`, `jettradb-1.jettradb`, etc.

## Cluster Management UI
Access the dashboard at `http://localhost:8080` (or Node IP).
- Go to **Cluster** tab.
- You can view status, add nodes, and remove nodes dynamically.
