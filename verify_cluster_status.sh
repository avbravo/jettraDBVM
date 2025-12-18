#!/bin/bash

# Start server in background
echo "Starting server..."
java -jar jettra-server/target/jettraDBVM.jar > server_output.log 2>&1 &
SERVER_PID=$!

echo "Waiting for server to start..."
sleep 10

# Check status
echo "Checking cluster status..."
curl -s -u admin:admin http://localhost:8080/api/cluster | jq .

# Stop server using new API
echo "Stopping server via API..."
curl -X POST -u admin:admin -H "Content-Type: application/json" -d '{"node":"localhost:8080"}' http://localhost:8080/api/cluster/stop

echo "Waiting for server to stop..."
wait $SERVER_PID
echo "Server stopped."
