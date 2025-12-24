#!/bin/bash

# Port for the test node
PORT=8099
DATA_DIR="test_data_no_fed"
CONFIG_FILE="test_config_no_fed.json"

# Clean up
rm -rf $DATA_DIR
mkdir -p $DATA_DIR

# Create config with FederatedServers but no running fed server
cat > $CONFIG_FILE <<EOF
{
  "Port": $PORT,
  "DataDir": "$DATA_DIR",
  "FederatedServers": ["http://localhost:9999"],
  "Bootstrap": true
}
EOF

echo "Starting JettraDB node on port $PORT in federated mode (orphaned)..."
java -jar jettra-server/target/jettraDBVM.jar $CONFIG_FILE > node_test.log 2>&1 &
NODE_PID=$!

# Wait for node to start
sleep 5

echo "--- Testing with CURL (Insert) ---"
curl -s -X POST "http://localhost:$PORT/api/doc?db=testdb&col=items" \
     -H "Authorization: Basic YWRtaW46YWRtaW4=" \
     -H "Content-Type: application/json" \
     -d '{"name": "test-item"}' > curl_res.txt

echo "HTTP Status for CURL:"
curl -o /dev/null -s -w "%{http_code}\n" -X POST "http://localhost:$PORT/api/doc?db=testdb&col=items" \
     -H "Authorization: Basic YWRtaW46YWRtaW4=" \
     -H "Content-Type: application/json" \
     -d '{"name": "test-item"}'

echo "Response Body:"
cat curl_res.txt
echo -e "\n"

# Verify with Java Driver if possible
# Since I cannot easily compile and run a Java app here without setting up a whole pom for the test,
# I will use a simple JShell or just trust the CURL result as the backend is the same for the driver.
# However, I can try to run a small java class using the built jars.

echo "--- Testing with Java Driver (Simple Class) ---"
cat > TestDriver.java <<EOF
import io.jettra.driver.JettraClient;
import java.util.Map;

public class TestDriver {
    public static void main(String[] args) {
        try {
            JettraClient client = new JettraClient("localhost", $PORT, "admin", "admin");
            client.saveDocument("testdb", "items", Map.of("name", "driver-item"));
            System.out.println("Driver: Save successful (Unexpected!)");
        } catch (Exception e) {
            System.out.println("Driver: Expected error caught -> " + e.getMessage());
        }
    }
}
EOF

# Compile and run TestDriver
# We use the shaded server jar for dependencies (jackson)
CP="jettra-driver/target/jettra-driver-1.0-SNAPSHOT.jar:jettra-server/target/jettraDBVM.jar"
javac -cp "$CP" TestDriver.java
java -cp ".:$CP" TestDriver

# Cleanup
echo "Stopping node..."
kill $NODE_PID
rm $CONFIG_FILE TestDriver.java TestDriver.class
# rm -rf $DATA_DIR
