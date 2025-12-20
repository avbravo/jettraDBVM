#!/bin/bash
# start_cluster.sh

echo "=== Setting up directories ==="
pkill -f jettraDBVM.jar
sleep 2

rm -rf data1 data2 data3
mkdir -p data1 data2 data3

# Create initial configs
echo '{"NodeID": "node1", "Port": 8080, "DataDir": "data1", "Bootstrap": true, "distributed": true}' > data1/config.json
echo '{"NodeID": "node2", "Port": 8081, "DataDir": "data2", "Bootstrap": false, "distributed": true}' > data2/config.json
echo '{"NodeID": "node3", "Port": 8082, "DataDir": "data3", "Bootstrap": false, "distributed": true}' > data3/config.json

echo "=== Starting Node 1 (Leader) ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data1/config.json > data1/server.log 2>&1 &
sleep 5

echo "=== Starting Node 2 ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data2/config.json > data2/server.log 2>&1 &
sleep 5

echo "=== Starting Node 3 ==="
java -cp jettra-server/target/jettraDBVM.jar:jettra-server/target/libs/* io.jettra.jettraDBVM.Main data3/config.json > data3/server.log 2>&1 &
sleep 5

echo "=== Registering Nodes ==="
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8081"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2
curl -s -u admin:admin -X POST -H "Content-Type: application/json" -d '{"url": "http://localhost:8082"}' http://localhost:8080/api/cluster/register
echo ""
sleep 2

echo "Cluster is up and running!"
