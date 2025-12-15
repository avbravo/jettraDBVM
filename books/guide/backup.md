# Backup and Restore Guide

JettraDB provides a simple yet robust mechanism for backing up your databases. Backups are stored as ZIP files containing the database collections and metadata.

You can trigger backups and restore data using:
1.  **Web Interface (Dashboard)**
2.  **JettraDB Shell**
3.  **REST API (cURL)**
4.  **Java Driver**

---

## 1. Web Interface

The easiest way to backup a database is through the JettraDB Web Dashboard.

### Creating a Backup
1.  Navigate to the **Dashboard**.
2.  In the sidebar, locate the database you wish to backup in the **Databases** tree.
3.  Click the **Backup Icon** (💾) next to the database name.
4.  Confirm the action when prompted.
5.  Once completed, a notification will appear, and the backup file will be downloaded automatically (or a link provided).

### Restoring a Backup
*Currently, restoration via the Web UI is planned for a future release. Please use the Shell or manual restoration methods.*

---

## 2. JettraDB Shell

You can manage backups directly from the command line using the JettraDB Shell.

### Creating a Backup
Use the `backup` command:

```bash
> use my_database
> backup
Success: Backup created at backups/my_database_20251214220000.zip
```

### Listing Backups
To see available backups:

```bash
> show backups
[
  "my_database_20251214220000.zip",
  "users_db_20251101100000.zip"
]
```

### Restoring a Backup
Use the `restore` command followed by the backup filename:

```bash
> restore my_database_20251214220000.zip
Success: Database 'my_database' restored from backups/my_database_20251214220000.zip
```

> **Note:** Restoring will overwrite existing data in the target database if it exists.

---

## 3. REST API (cURL)

You can automate backups using the HTTP API.

### Create Backup
**Endpoint:** `POST /api/backup`
**Query Param:** `db` (Database Name)

```bash
curl -X POST "http://localhost:8080/api/backup?db=my_database" \
     -H "Authorization: Basic YWRtaW46YWRtaW4="
```

**Response:**
```json
{
  "file": "my_database_20251214220000.zip"
}
```

### Download Backup
**Endpoint:** `GET /api/backup/download`
**Query Param:** `file` (Filename)

```bash
curl -O -J "http://localhost:8080/api/backup/download?file=my_database_20251214220000.zip" \
     -H "Authorization: Basic YWRtaW46YWRtaW4="
```

---

## 4. Java Driver

The JettraDB Java Driver supports backup operations programmatically.

### Example Code

```java
import io.jettra.driver.JettraClient;

public class BackupExample {
    public static void main(String[] args) {
        JettraClient client = new JettraClient("http://localhost:8080", "admin", "admin");

        try {
            // Trigger Backup
            String backupFile = client.backupDatabase("my_database");
            System.out.println("Backup created: " + backupFile);

            // List Backups
            List<String> backups = client.listBackups();
            for (String file : backups) {
                System.out.println("- " + file);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
```

---

## Backup File Format

Backups are standard ZIP files stored in the `backups/` directory of the JettraDB server.
The naming convention is:
`{database_name}_{YYYYMMDDHHMMSS}.zip`

**Contents:**
- `_info.json`: Database metadata.
- `{collection}.json`: Data for each collection.
- `{collection}.idx`: Index definitions (if applicable).
