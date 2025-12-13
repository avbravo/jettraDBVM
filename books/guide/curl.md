# JettraDB Curl Guide

This guide provides tested `curl` commands for interacting with JettraDB.

**Note**: The default credentials are `admin:adminadmin` (or as configured). The default port is `8080`.

## 1. Database Operations

### Create a Database
```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/api/dbs" \
     -H "Content-Type: application/json" \
     -d '{"name": "testdb"}'
```

### List Databases
```bash
curl -u admin:adminadmin -X GET "http://localhost:8080/api/dbs"
```

## 2. Collection Operations

### Create a Collection
```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/api/cols" \
     -H "Content-Type: application/json" \
     -d '{"database": "testdb", "collection": "users"}'
```

### List Collections
```bash
curl -u admin:adminadmin -X GET "http://localhost:8080/api/dbs/testdb/cols"
```

## 3. Document Operations

### Insert a Document
**Endpoint**: `/api/doc` (NOT `/db/doc`)

```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/api/doc?db=testdb&col=users" \
     -H "Content-Type: application/json" \
     -d '{"name": "Juan Perez", "email": "juan@example.com", "age": 30}'
```

### Get a Document
```bash
# Replace <ID> with the ID returned from the insert command
curl -u admin:adminadmin -X GET "http://localhost:8080/api/doc?db=testdb&col=users&id=<ID>"
```

### Query Documents
```bash
curl -u admin:adminadmin -X GET "http://localhost:8080/api/query?db=testdb&col=users"
```

## 4. Index Operations

### Create an Index
```bash
curl -u admin:adminadmin -X POST "http://localhost:8080/api/index" \
     -H "Content-Type: application/json" \
     -d '{
           "database": "testdb", 
           "collection": "users", 
           "field": "email", 
           "unique": true
         }'
```

### List Indexes
```bash
curl -u admin:adminadmin -X GET "http://localhost:8080/api/index?db=testdb&col=users"
```

## 5. Cluster Operations

### Get Node Status
```bash
curl -X GET "http://localhost:8080/raft/status"
```
