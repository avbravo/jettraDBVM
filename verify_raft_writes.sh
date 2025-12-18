#!/bin/bash
# verify_raft_writes.sh

cleanup() {
    echo "Stopping all servers..."
    kill $PID1 $PID2 $PID3 2>/dev/null
    echo "Cleaning up temp directories..."
    # rm -rf data1 data2 data3
}
trap cleanup EXIT

echo "=== Setting up directories ==="
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
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2

curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8082"}' http://localhost:8080/api/cluster/register
echo ""
sleep 5

echo "=== Verifying Cluster Membership ==="
node1_peers=$(curl -s -u admin:admin http://localhost:8080/api/cluster)
echo "Node 1 Cluster: $node1_peers"
if [[ $node1_peers != *"8081"* || $node1_peers != *"8082"* ]]; then
    echo "FAILURE: Node 1 does not see all peers"
    exit 1
fi

echo "=== Creating Database 'dist_test' on Leader ==="
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"name": "dist_test", "engine": "JettraBasicStore"}' http://localhost:8080/api/dbs
echo ""
sleep 3

echo "=== Verifying Database Replicated to Node 2 ==="
if [ -d "data2/dist_test" ]; then
    echo "SUCCESS: Database directory created on Node 2"
else
    echo "FAILURE: Database directory NOT created on Node 2"
    exit 1
fi

echo "=== Writing Document to Leader ==="
DOC_ID=$(curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"name": "Replicated Data", "value": 123}' "http://localhost:8080/api/doc?db=dist_test&col=test_col" | grep -o '"id":"[^"]*"' | cut -d'"' -f4)
echo "Document ID: $DOC_ID"

if [ -z "$DOC_ID" ]; then
    echo "FAILURE: No ID returned from save"
    exit 1
fi
sleep 2

echo "=== Verifying Data on Leader (Node 1) ==="
curl -s -u admin:admin "http://localhost:8080/api/doc?db=dist_test&col=test_col&id=$DOC_ID"
echo ""

echo "=== Verifying Data Replicated to Node 2 ==="
# Check via API on Node 2
RESP2=$(curl -s -u admin:admin "http://localhost:8081/api/doc?db=dist_test&col=test_col&id=$DOC_ID")
echo "Node 2 Response: $RESP2"

if [[ $RESP2 != *"Replicated Data"* ]]; then
    echo "FAILURE: Data not found on Node 2"
    # Debug: check file existence
    ls -l data2/dist_test/test_col/
    exit 1
fi

echo "=== Verifying Data Replicated to Node 3 ==="
RESP3=$(curl -s -u admin:admin "http://localhost:8082/api/doc?db=dist_test&col=test_col&id=$DOC_ID")
echo "Node 3 Response: $RESP3"

if [[ $RESP3 != *"Replicated Data"* ]]; then
    echo "FAILURE: Data not found on Node 3"
    exit 1
fi

echo "=== Testing Update Replication ==="
curl -s -u admin:admin -X PUT -H "Content-Type: application/json" -d '{"name": "Updated Data", "value": 456}' "http://localhost:8080/api/doc?db=dist_test&col=test_col&id=$DOC_ID"
sleep 2

RESP2_UPD=$(curl -s -u admin:admin "http://localhost:8081/api/doc?db=dist_test&col=test_col&id=$DOC_ID")
if [[ $RESP2_UPD != *"Updated Data"* ]]; then
    echo "FAILURE: Update not replicated to Node 2"
    echo "Got: $RESP2_UPD"
    exit 1
fi

echo "=== Testing Delete Replication ==="
curl -s -u admin:admin -X DELETE "http://localhost:8080/api/doc?db=dist_test&col=test_col&id=$DOC_ID"
sleep 2

if [ -f "data2/dist_test/test_col/$DOC_ID.jdb" ]; then
     echo "FAILURE: File still exists on Node 2 after delete"
     ls -l data2/dist_test/test_col/
     exit 1
fi

echo "ALL TESTS PASSED"
exit 0
