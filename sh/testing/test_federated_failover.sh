#!/bin/bash
cd "$(dirname "$0")/../.." || exit

# Build the project
echo "Building project..."
mvn clean package -DskipTests -f jettra-federated/pom.xml

FED_JAR="jettra-federated/target/jettraFederated.jar"

if [ ! -f "$FED_JAR" ]; then
    echo "Error: $FED_JAR not found!"
    exit 1
fi

# Cleanup previous background processes
cleanup() {
    echo "Cleaning up..."
    kill $(jobs -p) 2>/dev/null
    rm -f log/fed1.log log/fed2.log log/fed3.log log/mock_db.log
    exit
}
trap cleanup SIGINT SIGTERM

echo "Starting 3 Federated Servers..."

# Start Fed 1 on 9000 (Bootstrap enabled)
mkdir -p log
java -jar "$FED_JAR" 9000 fed-9000 http://localhost:9001 http://localhost:9002 -bootstrap > log/fed1.log 2>&1 &
PID1=$!
echo "Fed 1 started (PID: $PID1) on port 9000 with -bootstrap"

# Start Fed 2 on 9001
java -jar "$FED_JAR" 9001 fed-9001 http://localhost:9000 http://localhost:9002 > log/fed2.log 2>&1 &
PID2=$!
echo "Fed 2 started (PID: $PID2) on port 9001"

# Start Fed 3 on 9002
java -jar "$FED_JAR" 9002 fed-9002 http://localhost:9000 http://localhost:9001 > log/fed3.log 2>&1 &
PID3=$!
echo "Fed 3 started (PID: $PID3) on port 9002"

echo "Waiting for initialization..."
sleep 5

# Verify Feed 1 became leader immediately due to bootstrap
echo "Checking initial leader (should be Fed 1)..."
if curl -s http://localhost:9000/federated/status | grep -q '"raftState":"LEADER"'; then
    echo "Confirmed: Fed 1 is the initial Leader."
else
    echo "Warning: Fed 1 is not the leader yet. Checking others..."
fi

# Simulate a DB node registering with the leader
echo "Simulating DB node 'node-alfa' registration..."
curl -s -X POST -H "Content-Type: application/json" \
     -d '{"nodeId":"node-alfa", "url":"http://localhost:8080"}' \
     http://localhost:9000/federated/register

echo "Verifying DB leadership assignment..."
STATUS=$(curl -s http://localhost:9000/federated/status)
if echo "$STATUS" | grep -q '"leaderId":"node-alfa"'; then
    echo "Success: 'node-alfa' assigned as DB leader."
else
    echo "Error: DB leader assignment failed."
fi

echo "Stopping the current Federated Leader (Fed 1)..."
kill $PID1
echo "Waiting for failover election..."
sleep 12

echo "Checking for new leader among Fed 2 and Fed 3..."
NEW_LEADER_URL=""
if curl -s http://localhost:9001/federated/status | grep -q '"raftState":"LEADER"'; then
    echo "Fed 2 on port 9001 is the NEW LEADER!"
    NEW_LEADER_URL="http://localhost:9001"
elif curl -s http://localhost:9002/federated/status | grep -q '"raftState":"LEADER"'; then
    echo "Fed 3 on port 9002 is the NEW LEADER!"
    NEW_LEADER_URL="http://localhost:9002"
fi

if [ -z "$NEW_LEADER_URL" ]; then
    echo "Error: Failover failed. No new leader elected."
else
    echo "Verifying that the NEW LEADER recovered DB leadership state..."
    # The new leader should have node-alfa from the persistent state and promoted it
    NEW_STATUS=$(curl -s $NEW_LEADER_URL/federated/status)
    if echo "$NEW_STATUS" | grep -q '"leaderId":"node-alfa"'; then
        echo "Success: New leader recovered and maintains 'node-alfa' as DB leader."
    else
        echo "Error: New leader lost track of DB leadership."
    fi
fi

echo "Test complete."
cleanup
