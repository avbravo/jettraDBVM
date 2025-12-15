# Jettra Deployment Guide

## Overview
JettraDBVM can be deployed in multiple ways, ranging from standard JVM execution to containerized Docker environments and high-performance native binaries.

## 1. Docker
A `Dockerfile` is provided in the root directory for building a lightweight container.

### build
```bash
docker build -t jettradb .
```

### Run
```bash
docker run -p 8080:8080 -p 9000:9000 -v $(pwd)/data:/app/data jettradb
```
This maps port 8080 (HTTP) and 9000 (Raft) and persists data to the host's `data` directory.

## 2. GraalVM Native Image
For instant startup and lower memory footprint, you can compile JettraDB as a native binary.

### Prerequisites
- GraalVM JDK 21+
- Native Image tools installed (`gu install native-image`)

### Build
```bash
mvn package -Pnative
```
The binary will be generated in `jettra-server/target/jettraDBVM`.

## 3. CRaC (Coordinated Restore at Checkpoint)
JettraDB supports CRIU/CRaC for fast startup with full JVM peak performance.

### Prerequisites
- JDK with CRaC support (e.g., Azul Zulu Build of OpenJDK with CRaC)
- Linux environment

### Build
```bash
mvn package -Pcrac
```

### usage
1. **Checkpoint**: Run the application and trigger a checkpoint (implementation dependent, usually via jcmd or API).
2. **Restore**:
   ```bash
   java -XX:CRaCRestoreFrom=cr path/to/checkpoint
   ```

## 4. Postman Collection
A `jettra_postman_collection.json` is generated in the project root. Import this file into Postman to:
- Test API endpoints.
- Manage users and databases.
- Perform backups and restores.
- Verify versioning workflows.
