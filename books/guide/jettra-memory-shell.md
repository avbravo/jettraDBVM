# Jettra Memory Shell
 
The `jettra-memory-shell` is an interactive command-line interface for managing `jettra-memory` servers. It supports a wide range of commands for data manipulation, cluster management, and system administration.
 
## Getting Started
 
To run the shell:
 
```bash
java -jar jettra-memory-shell.jar --url http://localhost:9090 --user admin --pass adminadmin
```
 
## Available Commands
 
### Database Management
- `show dbs`: List all databases.
- `use <db>`: Switch to a specific database.
- `create db <name>`: Create a new database.
 
### Collection Management
- `show collections`: List collections in the current database.
- `create col <name>`: Create a new collection.
 
### Data Operations
- `insert <col> <json>`: Insert a document (e.g., `insert users {"name": "Alice"}`).
- `find <col> [json_query]`: Query documents.
- `delete <col> <id>`: Delete a document by ID.
 
### Transactions
- `begin`: Start a new transaction.
- `commit`: Commit the current transaction.
- `rollback`: Roll back the current transaction.
 
### Versioning
- `history <col> <id>`: View the version history of a document.
- `revert <col> <id> <version>`: Restore a document to a specific version.
 
### System Commands
- `cls` / `clear`: Clear the screen.
- `exit` / `quit`: Exit the shell.
- `help`: Display help information.
