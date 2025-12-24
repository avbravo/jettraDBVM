#!/bin/bash
cd "$(dirname "$0")/../.." || exit
# verify_leader_enforcement.sh

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

echo "=== Registering Nodes with Description ==="
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081", "description": "Worker Node 2"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2

curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8082", "description": "Worker Node 3"}' http://localhost:8080/api/cluster/register
echo ""
sleep 5

echo "=== Verifying Description in Cluster Status ==="
STATUS=$(curl -s -u admin:admin http://localhost:8080/api/cluster)
echo "Cluster Status: $STATUS"

if [[ $STATUS != *"Worker Node 2"* ]]; then
    echo "FAILURE: Description 'Worker Node 2' not found in cluster status"
    exit 1
fi

echo "=== Creating Database on Leader (Should Succeed) ==="
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"name": "leader_db", "engine": "JettraBasicStore"}' http://localhost:8080/api/dbs
echo ""
sleep 2

echo "=== Attempting Write on Follower (Node 2) - Should Fail ==="
# Try to create a database on Node 2 directly
RESP=$(curl -s -w "%{http_code}" -u admin:admin -X POST -H "Content-Type: application/json" -d '{"name": "follower_db", "engine": "JettraBasicStore"}' http://localhost:8081/api/dbs)
HTTP_CODE=${RESP: -3}
BODY=${RESP:0:${#RESP}-3}

echo "Response Code: $HTTP_CODE"
echo "Response Body: $BODY"

if [[ $HTTP_CODE != "503" ]]; then
    echo "FAILURE: Follower allow write! Expected 503, got $HTTP_CODE"
    exit 1
fi

echo "=== Attempting Save on Follower (Node 2) - Should Fail ==="
RESP=$(curl -s -w "%{http_code}" -u admin:admin -X POST -H "Content-Type: application/json" -d '{"val": 1}' "http://localhost:8081/api/doc?db=leader_db&col=test" )
HTTP_CODE=${RESP: -3}
echo "Save Response Code: $HTTP_CODE"

if [[ $HTTP_CODE != "503" ]]; then
    echo "FAILURE: Follower allow save! Expected 503, got $HTTP_CODE"
    exit 1
fi

echo "=== Attempting Write on Leader (Node 1) - Should Succeed ==="
RESP=$(curl -s -w "%{http_code}" -u admin:admin -X POST -H "Content-Type: application/json" -d '{"val": 1}' "http://localhost:8080/api/doc?db=leader_db&col=test" )
HTTP_CODE=${RESP: -3}
echo "Leader Save Response Code: $HTTP_CODE"

if [[ $HTTP_CODE != "200" ]]; then
    echo "FAILURE: Leader failed write! Expected 200, got $HTTP_CODE"
    exit 1
fi

echo "ALL TESTS PASSED"
exit 0
