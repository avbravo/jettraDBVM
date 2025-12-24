#!/bin/bash
cd "$(dirname "$0")/../.." || exit

# Configuration
JAR_FILE="jettra-server/target/jettraDBVM.jar"
if [ ! -f "$JAR_FILE" ]; then
    echo "JAR not found at $JAR_FILE. Using shaded jar..."
    JAR_FILE="jettra-server/target/jettra-server-1.0-SNAPSHOT-shaded.jar"
fi

# Clean up
rm -rf cluster_test
mkdir -p cluster_test/node1
mkdir -p cluster_test/node2

cleanup() {
    echo "Stopping nodes..."
    pkill -f "jettraDBVM"
}
trap cleanup EXIT

# Helper to start node
start_node() {
    local dir=$1
    local port=$2
    local bootstrap=$3
    
    cat > $dir/config.json <<EOF
{
  "Host": "localhost",
  "Port": $port,
  "DataDir": "$dir/data",
  "Bootstrap": $bootstrap,
  "Peers": [],
  "NodeID": "$(basename $dir)",
  "distributed": true,
  "system_username": "admin",
  "system_password": "password"
}
EOF
    # Note: system_username/password in config might be needed if defaults are not admin/admin
    # Assuming default admin/admin if not set, or we set them.
    # But wait, existing code uses Engine.getAuth().getUserRole("_system", username).
    # Does clean install create admin user?
    # Engine.java usually initializes default users if missing.
    
    echo "Starting node in $dir on port $port..."
    cd $dir
    java -jar ../../$JAR_FILE > node.log 2>&1 &
    cd ../..
}

# Start Node 1 (Bootstrap)
start_node "cluster_test/node1" 8081 true

# Wait for Node 1
echo "Waiting for Node 1..."
sleep 5

# Check Status Node 1
STATUS_1=$(curl -s -u admin:admin http://localhost:8081/api/cluster)
echo "Node 1 Status: $STATUS_1"

# Start Node 2 (Follower)
start_node "cluster_test/node2" 8082 false

# Wait for Node 2
echo "Waiting for Node 2..."
sleep 5

# Register Node 2 with Node 1
echo "Registering Node 2 with Node 1..."
curl -X POST http://localhost:8081/api/cluster/register \
     -u admin:admin \
     -H "Content-Type: application/json" \
     -d '{"url": "http://localhost:8082"}'

echo ""
sleep 2

# Check Node 1 Peers
echo "Checking Node 1 Config..."
# Verify via API
curl -s -u admin:admin http://localhost:8081/api/cluster | grep "http://localhost:8082" && echo "SUCCESS: Node 1 has Node 2 (API)" || echo "FAIL: Node 1 missing Node 2 (API)"

# Verify federated.json content
echo "Checking federated.json on Node 1..."
if grep -q "http://localhost:8082" cluster_test/node1/data/federated.json; then
    echo "SUCCESS: federated.json on Node 1 contains Node 2"
else
    echo "FAIL: federated.json on Node 1 missing Node 2"
    cat cluster_test/node1/data/federated.json
    exit 1
fi

# Check Node 2 Peers (Propagation)
echo "Checking Node 2 Config..."
# Verify via API
curl -s -u admin:admin http://localhost:8082/api/cluster | grep "http://localhost:8081" && echo "SUCCESS: Node 2 has Node 1 (API)" || echo "FAIL: Node 2 missing Node 1 (API)"

# Verify federated.json on Node 2
echo "Checking federated.json on Node 2..."
if grep -q "http://localhost:8081" cluster_test/node2/data/federated.json; then
    echo "SUCCESS: federated.json on Node 2 contains Node 1"
else
    echo "FAIL: federated.json on Node 2 missing Node 1"
    cat cluster_test/node2/data/federated.json
    exit 1
fi

# Deregister Node 2
echo "Deregistering Node 2 from Node 1..."
curl -X POST http://localhost:8081/api/cluster/deregister \
     -u admin:admin \
     -H "Content-Type: application/json" \
     -d '{"url": "http://localhost:8082"}'

echo ""
sleep 2

# Check Node 1 Peers (Should be empty of node 2)
echo "Checking Node 1 Config after removal..."
curl -s -u admin:admin http://localhost:8081/api/cluster | grep "http://localhost:8082" && echo "FAIL: Node 1 still has Node 2" || echo "SUCCESS: Node 1 removed Node 2"

echo "Verification Complete."
