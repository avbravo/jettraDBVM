# Restore Guide

JettraDB supports restoring databases from zip backups via the Web Interface, Shell, and API.

## 1. Web Interface (Deploy Restore)

You can restore a backup from a local zip file on your computer.

1.  Navigate to the **Backups** view.
2.  Click the **Deploy Restore** button.
3.  Select your local `.zip` backup file.
4.  Enter the **Target Database Name**.
    *   *Warning*: If the database exists, it will be overwritten.
5.  Click **Upload & Restore**.

## 2. Shell Command

The JettraDB Shell allows you to restore a local file to a specific database.

```bash
> restore <path_to_zip> <target_db>
```

**Example:**
```bash
> restore ./exports/mydb_2024.zip production_db
```

## 3. Storage-Based Restore

If you have access to the server's filesystem, you can place backups in the `backups/` directory and use the backend's restore feature (or the existing "Restore" button next to listed backups in the Web UI).

## 4. CURL / API

You can upload and restore via `curl` by POSTing the binary content to `/api/restore/upload`.

```bash
curl -X POST --data-binary @mybackup.zip \
     "http://localhost:8080/api/restore/upload?db=restored_db" \
     -H "Authorization: Basic YWRtaW46YWRtaW4=" \
     -H "Content-Type: application/octet-stream"
```

**Parameters:**
*   `db`: The name of the database to restore into.
*   Body: The binary content of the zip file.
