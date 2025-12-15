#!/bin/bash
# Verify JettraDB Backup API

BASE_URL="http://localhost:8080"
AUTH="Authorization: Basic YWRtaW46YWRtaW4=" # admin:admin

echo "1. Creating test DB..."
curl -s -X POST "$BASE_URL/api/dbs" -H "$AUTH" -d '{"name":"testbackupdb"}'

echo -e "\n2. Adding some data..."
curl -s -X POST "$BASE_URL/api/cols" -H "$AUTH" -d '{"database":"testbackupdb", "collection":"users"}'
curl -s -X POST "$BASE_URL/api/doc?db=testbackupdb&col=users" -H "$AUTH" -d '{"name":"Alice"}'

echo -e "\n3. Triggering Backup..."
RESPONSE=$(curl -s -X POST "$BASE_URL/api/backup?db=testbackupdb" -H "$AUTH")
echo "Response: $RESPONSE"

FILE=$(echo $RESPONSE | grep -o 'testbackupdb_[0-9]*\.zip')

if [ -z "$FILE" ]; then
    echo "Backup failed!"
    exit 1
fi

echo "Backup created: $FILE"

echo -e "\n4. Downloading Backup..."
curl -s -X GET "$BASE_URL/api/backup/download?file=$FILE" -H "$AUTH" --output downloaded_backup.zip

if [ -f "downloaded_backup.zip" ]; then
    echo "Download success!"
    ls -l downloaded_backup.zip
    rm downloaded_backup.zip
else
    echo "Download failed!"
    exit 1
fi

echo -e "\n5. Cleanup..."
curl -s -X DELETE "$BASE_URL/api/dbs?name=testbackupdb" -H "$AUTH"

echo -e "\nVerification Complete!"
