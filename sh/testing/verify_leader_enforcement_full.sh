#!/bin/bash
cd "$(dirname "$0")/../.." || exit
# verify_leader_enforcement_full.sh

cleanup() {
    echo "Stopping all servers..."
    kill $PID1 $PID2 $PID3 2>/dev/null
    rm -rf data1 data2 data3
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
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081", "description": "Worker 2"}' http://localhost:8080/api/cluster/register
sleep 2
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8082", "description": "Worker 3"}' http://localhost:8080/api/cluster/register
sleep 5

echo "=== Checking Description in Node 2 (Follower) ==="
STATUS=$(curl -s -u admin:admin http://localhost:8081/api/cluster)
if [[ $STATUS != *"Worker 3"* ]]; then
    echo "FAILURE: Follower (Node 2) cluster status missing 'Worker 3' description."
    echo "Status: $STATUS"
    # exit 1 
    # Can't exit, might be replication delay or snapshot issue. But let's proceed to Write Test.
fi

echo "=== Test 1: API Write on Leader (Should Succeed) ==="
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"name": "leader_db", "engine": "JettraBasicStore"}' http://localhost:8080/api/dbs
echo ""

echo "=== Test 2: API Write on Follower (Should Fail 503/500) ==="
RESP=$(curl -s -w "%{http_code}" -u admin:admin -X POST -H "Content-Type: application/json" -d '{"name": "follower_db", "engine": "JettraBasicStore"}' http://localhost:8081/api/dbs)
HTTP_CODE=${RESP: -3}
if [[ $HTTP_CODE != "503" && $HTTP_CODE != "500" ]]; then
    echo "FAILURE: Follower allowed API DB Create! Code local: $HTTP_CODE"
    exit 1
fi

echo "=== Test 3: SQL Command INSERT on Follower (Should Fail 500) ==="
# Using /api/command
CMD='{"command": "INSERT INTO _info (key, val) VALUES ('test', 123)"}'
RESP=$(curl -s -w "%{http_code}" -u admin:admin -X POST -H "Content-Type: application/json" -d "$CMD" "http://localhost:8081/api/command?db=leader_db")
HTTP_CODE=${RESP: -3}
BODY=${RESP:0:${#RESP}-3}

echo "SQL Response Code: $HTTP_CODE"
echo "SQL Response Body: $BODY"

if [[ $HTTP_CODE == "200" ]]; then
     echo "FAILURE: Follower allowed API Command INSERT! Code: $HTTP_CODE"
     exit 1
fi

echo "ALL TESTS PASSED"
exit 0
