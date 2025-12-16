#!/bin/bash
# Benchmark JettraStoreEngine
# Usage: ./benchmark_engine.sh [count]

COUNT=${1:-1000}
DB_NAME="bench_engine_db"
COL_NAME="data"

echo "Starting Benchmark (Count: $COUNT)..."

# Login
LOGIN_RES=$(curl -s -X POST http://localhost:8080/api/login -d '{"username":"admin","password":"adminadmin"}' -H "Content-Type: application/json")
TOKEN=$(echo "$LOGIN_RES" | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

if [ -z "$TOKEN" ]; then
    echo "ERROR: Token is empty"
    exit 1
fi

# Create DB (ignore error if exists)
curl -s -X POST http://localhost:8080/api/dbs -H "Authorization: $TOKEN" -H "Content-Type: application/json" -d "{\"name\":\"$DB_NAME\", \"engine\":\"JettraEngineStore\"}" > /dev/null

echo "Inserting $COUNT documents..."
START=$(date +%s%N)

for i in $(seq 1 $COUNT); do
    curl -s -X POST "http://localhost:8080/api/doc?db=$DB_NAME&col=$COL_NAME" -H "Authorization: $TOKEN" -H "Content-Type: application/json" -d "{\"id\":\"doc$i\", \"name\":\"User $i\", \"bio\":\"This is a bio for user $i which is a bit longer to test compression.\", \"age\":$i, \"active\":true}" > /dev/null
done

END=$(date +%s%N)
DURATION=$(( ($END - $START) / 1000000 ))

echo "Insertions took $DURATION ms"

# Measure Size
SIZE=$(du -sh jettra-server/data/$DB_NAME/$COL_NAME | cut -f1)
BYTES=$(du -sb jettra-server/data/$DB_NAME/$COL_NAME | cut -f1)

echo "Total Size: $SIZE ($BYTES bytes)"
echo "Average Size per Doc: $(( $BYTES / $COUNT )) bytes"
