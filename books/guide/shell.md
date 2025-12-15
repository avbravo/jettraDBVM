# Jettra Shell Guide

## Overview
The Jettra Shell is a command-line interface for interacting with JettraDB.

## Connecting and Authenticating
Default connection is to `http://localhost:8080`.
```bash
jettra> connect http://localhost:8080
jettra> login admin admin
```

## User Management
Create new users with specific roles and database access.
```bash
jettra> create user <username>
```
Follow the interactive prompts to set:
- **Password**
- **Role**: `admin`, `owner`, `writereader`, `reader`
- **Allowed DBs**: Comma-separated list (e.g. `db1,db2`) or `*` for all.

## Database Navigation
```bash
jettra> show dbs
jettra> use my_db
jettra> show collections
```

## Data Manipulation
### Insert
```bash
jettra> insert my_col {"name": "Test", "value": 123}
```
### Find (Paginated)
```bash
jettra> find my_col
```
You will see 10 results. Use the menu to navigate:
- `[N]ext`: Next page
- `[B]ack`: Previous page
- `[F]irst`: First page
- `[Q]uit`: Exit pagination

## Backup and Restore
```bash
jettra> backup my_db
jettra> restore my_backup.zip my_db_restored
```

## Versioning
Manage document history directly from the shell.

### History
List all available versions for a document.
```bash
jettra> history my_collection doc_id
```

### Revert
Restore a document to a previous version.
```bash
jettra> revert my_collection doc_id 1798362512344
```
