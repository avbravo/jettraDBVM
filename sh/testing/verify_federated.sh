#!/bin/bash
cd "$(dirname "$0")/../.." || exit
# verify_federated.sh

echo "Starting Federated Server verification..."

# 1. Start Federated Server in background
mvn -pl jettra-federated exec:java -Dexec.mainClass="io.jettra.federated.FederatedMain" -Dexec.args="9000 fed-1" > federated.log 2>&1 &
FED_PID=$!
echo "Federated Server started (PID: $FED_PID)"

sleep 5

# 2. Register a mock DB node
echo "Registering mock DB node..."
curl -X POST http://localhost:9000/federated/register \
     -H "Content-Type: application/json" \
     -d '{"nodeId": "db-1", "url": "http://localhost:8080"}'

# 3. Check status
echo "Checking Federated status..."
curl http://localhost:9000/federated/status

# 4. Send heartbeat
echo "Sending heartbeat..."
curl -X POST "http://localhost:9000/federated/heartbeat?nodeId=db-1"

# 5. Check status again
echo "Checking status after heartbeat..."
curl http://localhost:9000/federated/status

# Cleanup
kill $FED_PID
echo "Federated Server stopped."
