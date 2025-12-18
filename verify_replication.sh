#!/bin/bash

# Configuration
NODE1_PORT=8081
NODE2_PORT=8082
HEADER="Content-Type: application/json"
AUTH="-u admin:admin"

# Cleanup
echo "Cleaning up..."
pkill -f jettraDBVM.jar
rm -rf cluster_test
mkdir -p cluster_test/node1/data
mkdir -p cluster_test/node2/data

# Create config.json
cat <<EOF > cluster_test/node1/config.json
{
  "Port": $NODE1_PORT,
  "DataDir": "data",
  "distributed": true,
  "NodeID": "node1",
  "Bootstrap": true,
  "system_username": "admin",
  "system_password": "admin"
}
EOF

cat <<EOF > cluster_test/node2/config.json
{
  "Port": $NODE2_PORT,
  "DataDir": "data",
  "distributed": true,
  "NodeID": "node2",
  "system_username": "admin",
  "system_password": "admin"
}
EOF

# Copy jar
JAR_SOURCE="jettra-server/target/jettraDBVM.jar"
if [ ! -f "$JAR_SOURCE" ]; then
    echo "JAR not found at $JAR_SOURCE. Trying shaded..."
    JAR_SOURCE="jettra-server/target/jettra-server-1.0-SNAPSHOT-shaded.jar"
fi

if [ ! -f "$JAR_SOURCE" ]; then
    echo "JAR NOT FOUND!"
    exit 1
fi

cp $JAR_SOURCE cluster_test/node1/jettraDBVM.jar
cp $JAR_SOURCE cluster_test/node2/jettraDBVM.jar

# Start Node 1
echo "Starting Node 1..."
cd cluster_test/node1
nohup java -jar jettraDBVM.jar > node1.log 2>&1 &
NODE1_PID=$!
cd ../..
sleep 5

# Create DB on Node 1 (Before Node 2 joins)
echo "Creating database 'db_pre' on Node 1..."
curl $AUTH -X POST http://localhost:$NODE1_PORT/api/dbs -H "$HEADER" -d '{"name": "db_pre", "engine": "JettraBasicStore"}'
echo ""

# Start Node 2
echo "Starting Node 2..."
cd cluster_test/node2
nohup java -jar jettraDBVM.jar > node2.log 2>&1 &
NODE2_PID=$!
cd ../..
sleep 5

# Register Node 2
echo "Registering Node 2 with Leader..."
curl $AUTH -X POST http://localhost:$NODE1_PORT/api/cluster/register -H "$HEADER" -d '{"url": "http://localhost:8082"}'
echo ""
sleep 5

# Verify Node 2 has 'db_pre' (Snapshot test)
echo "Verifying Node 2 has 'db_pre'..."
curl $AUTH http://localhost:$NODE2_PORT/api/dbs > node2_dbs.json
if grep -q "db_pre" node2_dbs.json; then
    echo "SUCCESS: db_pre found on Node 2 (Snapshot replication worked)"
else
    echo "FAILURE: db_pre NOT found on Node 2"
    cat node2_dbs.json
fi

# Create another DB on Node 1 (After Node 2 joined)
echo "Creating database 'db_post' on Node 1..."
curl $AUTH -X POST http://localhost:$NODE1_PORT/api/dbs -H "$HEADER" -d '{"name": "db_post", "engine": "JettraBasicStore"}'
echo ""
sleep 2

# Verify Node 2 has 'db_post' (Command replication test)
echo "Verifying Node 2 has 'db_post'..."
curl $AUTH http://localhost:$NODE2_PORT/api/dbs > node2_dbs_post.json
if grep -q "db_post" node2_dbs_post.json; then
    echo "SUCCESS: db_post found on Node 2 (Command replication worked)"
else
    echo "FAILURE: db_post NOT found on Node 2"
    cat node2_dbs_post.json
fi

# Determine Leader via UI status on Node 2
echo "Node 2 Status:"
curl $AUTH http://localhost:$NODE2_PORT/api/cluster

# Cleanup
kill $NODE1_PID
kill $NODE2_PID
