#!/bin/bash
cd "$(dirname "$0")/../.." || exit

# Compile first (assuming already compiled or run mvn install)
# mvn clean install -DskipTests

JAR="jettra-server/target/jettraDBVM.jar"

if [ ! -f "$JAR" ]; then
    echo "Jar not found at $JAR. Please run 'mvn install -DskipTests' first."
    exit 1
fi

echo "Setting up 3-node cluster..."

# Cleanup
rm -rf cluster_test
mkdir -p cluster_test/node1/data
mkdir -p cluster_test/node2/data
mkdir -p cluster_test/node3/data

# Configs
cat > cluster_test/node1/config.json <<EOF
{
  "Host": "0.0.0.0",
  "Port": 9001,
  "DataDir": "data",
  "distributed": true,
  "NodeID": "node1",
  "Peers": ["http://localhost:9002", "http://localhost:9003"],
  "adminUser": "admin",
  "adminPass": "admin"
}
EOF

cat > cluster_test/node2/config.json <<EOF
{
  "Host": "0.0.0.0",
  "Port": 9002,
  "DataDir": "data",
  "distributed": true,
  "NodeID": "node2",
  "Peers": ["http://localhost:9001", "http://localhost:9003"],
  "adminUser": "admin",
  "adminPass": "admin"
}
EOF

cat > cluster_test/node3/config.json <<EOF
{
  "Host": "0.0.0.0",
  "Port": 9003,
  "DataDir": "data",
  "distributed": true,
  "NodeID": "node3",
  "Peers": ["http://localhost:9001", "http://localhost:9002"],
  "adminUser": "admin",
  "adminPass": "admin"
}
EOF

# Start Nodes
echo "Starting Node 1..."
cd cluster_test/node1
nohup java -jar ../../$JAR > node.log 2>&1 &
PID1=$!
cd ../..

echo "Starting Node 2..."
cd cluster_test/node2
nohup java -jar ../../$JAR > node.log 2>&1 &
PID2=$!
cd ../..

echo "Starting Node 3..."
cd cluster_test/node3
nohup java -jar ../../$JAR > node.log 2>&1 &
PID3=$!
cd ../..

echo "Nodes started. PIDs: $PID1, $PID2, $PID3"
echo "Waiting 15 seconds for elections..."
sleep 15

# Function to check status
check_status() {
    PORT=$1
    echo ">>> Checking Node on Port $PORT"
    # Login to get token first (mocking auth or assuming auth disabled for this endpoint? WebServices has authMiddleware)
    # Login
    TOKEN=$(curl -s -X POST http://localhost:$PORT/api/login -H "Content-Type: application/json" -d '{"username":"admin", "password":"admin"}' | jq -r .token)
    
    if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
        echo "Failed to login to node $PORT"
        return
    fi

    # Cluster Status
    curl -s -X GET http://localhost:$PORT/api/cluster -H "Authorization: $TOKEN" | jq .
}

check_status 9001
check_status 9002
check_status 9003

echo "Stopping nodes..."
kill $PID1 $PID2 $PID3
wait $PID1 $PID2 $PID3 2>/dev/null

echo "Done."
