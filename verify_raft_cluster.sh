#!/bin/bash
# verify_raft_cluster.sh

# Cleanup function
cleanup() {
    echo "Stopping all servers..."
    kill $PID1 $PID2 $PID3 2>/dev/null
    echo "Cleaning up temp directories..."
    # rm -rf data1 data2 data3
}
trap cleanup EXIT


echo "=== Setting up directories ==="
# Kill any existing instances
pkill -f jettraDBVM.jar
sleep 2

rm -rf data1 data2 data3
mkdir -p data1 data2 data3

# Create initial configs
echo '{"Port": 8080, "DataDir": "data1", "Bootstrap": true, "distributed": true}' > data1/config.json
echo '{"Port": 8081, "DataDir": "data2", "Bootstrap": false, "distributed": true}' > data2/config.json
echo '{"Port": 8082, "DataDir": "data3", "Bootstrap": false, "distributed": true}' > data3/config.json

echo "=== Starting Node 1 (Leader) ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data1/config.json > data1/server.log 2>&1 &
PID1=$!
sleep 5

echo "=== Starting Node 2 ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data2/config.json > data2/server.log 2>&1 &
PID2=$!
sleep 5

echo "=== Starting Node 3 ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data3/config.json > data3/server.log 2>&1 &
PID3=$!
sleep 5

echo "=== Registering Nodes ==="
# Register Node 2
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2

# Register Node 3
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8082"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2

echo "=== Verifying Cluster Sync ==="
echo "Node 1 Status:"
curl -s -u admin:admin http://localhost:8080/api/cluster | grep "nodes"
echo "Node 2 Status (Should have all nodes):"
curl -s -u admin:admin http://localhost:8081/api/cluster | grep "nodes"
echo "Node 3 Status (Should have all nodes):"
curl -s -u admin:admin http://localhost:8082/api/cluster | grep "nodes"

echo "=== Testing Stop Node Propagation ==="
# Stop Node 2 via Leader
echo "Stopping Node 2 via Leader API..."
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"node": "http://localhost:8081"}' http://localhost:8080/api/cluster/stop
echo ""
sleep 5

echo "=== Verifying Inactive Status Propagation ==="
echo "Node 1 View (Node 2 should be INACTIVE):"
curl -s -u admin:admin http://localhost:8080/api/cluster | grep "INACTIVE"
echo "Node 3 View (Node 2 should be INACTIVE):"
curl -s -u admin:admin http://localhost:8082/api/cluster | grep "INACTIVE"

# Manual check
count=$(curl -s -u admin:admin http://localhost:8082/api/cluster | grep "INACTIVE" | wc -l)
if [ "$count" -gt 0 ]; then
    echo "SUCCESS: Inactive status propagated to Node 3"
else
    echo "FAILURE: Inactive status NOT propagated to Node 3"
    exit 1
fi
