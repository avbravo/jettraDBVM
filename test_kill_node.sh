#!/bin/bash
# test_kill_node.sh

# Cleanup function
cleanup() {
    echo "Stopping all servers..."
    kill $PID1 $PID2 2>/dev/null
}
trap cleanup EXIT

echo "=== Setting up directories ==="
pkill -f jettraDBVM.jar
sleep 2

rm -rf data1 data2
mkdir -p data1 data2

echo '{"Port": 8080, "DataDir": "data1", "Bootstrap": true, "distributed": true}' > data1/config.json
echo '{"Port": 8081, "DataDir": "data2", "Bootstrap": false, "distributed": true}' > data2/config.json

echo "=== Starting Node 1 (Leader) ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data1/config.json > data1/server.log 2>&1 &
PID1=$!
sleep 5

echo "=== Starting Node 2 ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data2/config.json > data2/server.log 2>&1 &
PID2=$!
sleep 5

echo "=== Registering Node 2 ==="
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2

echo "=== Checking Status (Before Kill) ==="
curl -s -u admin:admin http://localhost:8080/api/cluster | grep -o '"status":"ACTIVE"'

echo "=== Killing Node 2 ==="
kill $PID2
sleep 10 # Wait for heartbeats to fail and mark inactive

echo "=== Checking Status (After Kill) ==="
status=$(curl -s -u admin:admin http://localhost:8080/api/cluster)
echo $status | grep -o '"url":"http://localhost:8081","status":"[A-Z]*"'

if echo $status | grep -q '"url":"http://localhost:8081","status":"INACTIVE"'; then
    echo "SUCCESS: Leader detected Node 2 is down"
else
    echo "FAILURE: Leader did NOT mark Node 2 as INACTIVE"
    exit 1
fi
