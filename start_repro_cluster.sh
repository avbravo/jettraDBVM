#!/bin/bash
# start_repro_cluster.sh
# Starts a 3-node cluster and monitors for leader flapping.

mkdir -p repro/node1 repro/node2 repro/node3

# Configs
echo '{"Port": 8080, "DataDir": "repro/node1", "Bootstrap": true, "distributed": true}' > repro/node1/config.json
echo '{"Port": 8081, "DataDir": "repro/node2", "Bootstrap": false, "distributed": true}' > repro/node2/config.json
echo '{"Port": 8082, "DataDir": "repro/node3", "Bootstrap": false, "distributed": true}' > repro/node3/config.json

# Start servers
echo "Starting Node 1..."
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main repro/node1/config.json > repro/node1/server.log 2>&1 &
PID1=$!
sleep 5

echo "Starting Node 2..."
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main repro/node2/config.json > repro/node2/server.log 2>&1 &
PID2=$!
sleep 5

echo "Starting Node 3..."
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main repro/node3/config.json > repro/node3/server.log 2>&1 &
PID3=$!
sleep 5

echo "Registering nodes..."
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081"}' http://localhost:8080/api/cluster/register
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8082"}' http://localhost:8080/api/cluster/register

echo "Cluster started. PIDs: $PID1, $PID2, $PID3"
echo "Monitor with: ./test_cluster_curl.sh http://localhost:8080"
