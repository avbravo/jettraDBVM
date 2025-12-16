#!/bin/bash
# Benchmark both JettraBasicStore and JettraEngineStore

echo "========================================"
echo "    JettraDB Benchmark Comparison"
echo "========================================"

COUNT=${1:-1000}
TOKEN=""

# Login
echo "Logging in..."
LOGIN_RES=$(curl -s -X POST http://localhost:8080/api/login -d '{"username":"admin","password":"adminadmin"}' -H "Content-Type: application/json")
TOKEN=$(echo "$LOGIN_RES" | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

if [ -z "$TOKEN" ]; then
    echo "ERROR: Login failed. Token is empty."
    exit 1
fi

run_benchmark() {
    ENGINE=$1
    DB_NAME=$2
    COL_NAME="data"

    echo ""
    echo ">>> Benchmarking $ENGINE ($DB_NAME)"
    
    # Create DB
    curl -s -X POST http://localhost:8080/api/dbs -H "Authorization: $TOKEN" -H "Content-Type: application/json" -d "{\"name\":\"$DB_NAME\", \"engine\":\"$ENGINE\"}" > /dev/null

    echo "Inserting $COUNT documents..."
    START=$(date +%s%N)
    
    for i in $(seq 1 $COUNT); do
        curl -s -X POST "http://localhost:8080/api/doc?db=$DB_NAME&col=$COL_NAME" -H "Authorization: $TOKEN" -H "Content-Type: application/json" -d "{\"id\":\"doc$i\", \"name\":\"User $i\", \"bio\":\"This is a bio for user $i which is a bit longer to test compression and optimization.\", \"age\":$i, \"active\":true, \"data\": [1,2,3,4,5]}" > /dev/null
    done
    
    END=$(date +%s%N)
    DURATION=$(( ($END - $START) / 1000000 ))
    echo "Insertions took $DURATION ms"

    # Size
    SIZE=$(du -sh jettra-server/data/$DB_NAME/$COL_NAME 2>/dev/null | cut -f1)
    BYTES=$(du -sb jettra-server/data/$DB_NAME/$COL_NAME 2>/dev/null | cut -f1)
    
    echo "Total Size: $SIZE ($BYTES bytes)"
    echo "Average Size per Doc: $(( $BYTES / $COUNT )) bytes"
}

# Run Basic
run_benchmark "JettraBasicStore" "bench_basic_db"

# Run Engine
run_benchmark "JettraEngineStore" "bench_engine_db"

echo ""
echo "========================================"
echo "Benchmark Complete"
