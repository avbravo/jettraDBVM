#!/bin/bash
cd "$(dirname "$0")/../.." || exit
echo "Starting Verification..."
# Login
LOGIN_RES=$(curl -s -X POST http://localhost:8080/api/login -d '{"username":"admin","password":"adminadmin"}' -H "Content-Type: application/json")
echo "Login Response: $LOGIN_RES"
TOKEN=$(echo "$LOGIN_RES" | python3 -c "import sys, json; print(json.load(sys.stdin)['token'])")

if [ -z "$TOKEN" ]; then
    echo "ERROR: Token is empty"
    exit 1
fi
echo "Got Token: $TOKEN"

# Create DB (ignore error if exists)
echo "Creating DB testdb_engine..."
curl -s -X POST http://localhost:8080/api/dbs -H "Authorization: $TOKEN" -H "Content-Type: application/json" -d '{"name":"testdb_engine", "engine":"JettraEngineStore"}'
echo ""

# Insert Doc
echo "Inserting Doc..."
curl -v -X POST "http://localhost:8080/api/doc?db=testdb_engine&col=users" -H "Authorization: $TOKEN" -H "Content-Type: application/json" -d '{"name":"DebugUser", "age":100}' 2>&1
echo ""

# Query
echo "Querying..."
curl -s "http://localhost:8080/api/query?db=testdb_engine&col=users" -H "Authorization: $TOKEN"
echo ""
