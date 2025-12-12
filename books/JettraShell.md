# JettraShell Guide

The JettraShell provides a powerful command-line interface to interact with JettraDB instances. It supports multiple query languages including **JettraQueryLanguage (JQL)**, **SQL (Subset)**, and **MongoDB-like** syntax.

## Connection
```bash
jettra> connect http://localhost:8080
jettra> login admin password
```

## Basic Operations (Shell Commands)
```bash
jettra> show dbs
jettra> use users_db
jettra> show collections
jettra> create db my_database
jettra> create col my_collection
jettra> drop db my_database
jettra> drop col my_collection
```

## CRUD Operations

### Insert
**JQL**: `INSERT INTO users DOC {"name": "Alice", "age": 30}`
**Mongo**: `db.users.insert({"name": "Alice", "age": 30})`
**SQL**: `INSERT INTO users (name, age) VALUES ('Alice', 30)`

### Find / Read
**JQL**: `FIND IN users WHERE age > 20 LIMIT 5`
**Mongo**: `db.users.find({"age": {"$gt": 20}}).limit(5)`
**SQL**: `SELECT * FROM users WHERE age > 20 LIMIT 5`

### Update
**JQL**: `UPDATE IN users SET age = 31 WHERE name = "Alice"`
**Mongo**: `db.users.update({"name": "Alice"}, {"$set": {"age": 31}})`
**SQL**: `UPDATE users SET age = 31 WHERE name = 'Alice'`

### Delete
**JQL**: `DELETE FROM users WHERE age < 18`
**Mongo**: `db.users.remove({"age": {"$lt": 18}})`
**SQL**: `DELETE FROM users WHERE age < 18`

## Index Management
Indexes improve query performance.

**Create Index**:
```bash
# JQL
CREATE INDEX ON users (email) UNIQUE
# Mongo
db.users.createIndex({"email": 1}, {"unique": true})
```

**List Indexes**:
```bash
SHOW INDEXES ON users
```

## Aggregations
For complex data analysis, use the aggregation pipeline.

**JQL**:
```bash
AGGREGATE IN orders PIPELINE [
    {"$group": {"_id": "$customerId", "total": {"$sum": "$amount"}}},
    {"$sort": {"total": -1}}
]
```

**SQL**:
```sql
SELECT customerId, SUM(amount) as total FROM orders GROUP BY customerId ORDER BY total DESC
```

## References (Joins)
JettraDB supports looking up data from other collections.

**JQL**:
```bash
// Lookup 'role' from 'roles' collection where roles._id = users.roleId
FIND IN users JOIN roles ON roleId = _id
```
