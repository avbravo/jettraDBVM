#!/bin/bash
cd "$(dirname "$0")/../.." || exit

# Output file
OUTPUT_FILE="$(pwd)/books/guide/result.md"
mkdir -p "$(dirname "$OUTPUT_FILE")"

echo "# JettraDB Benchmark Results" > "$OUTPUT_FILE"
echo "Date: $(date)" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "Running benchmarks with Java 25 Compact Object Headers optimization." >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Stop any running server
echo "Stopping any existing JettraDB instance..."
pkill -f "jettraDBVM.jar"
sleep 2

# Start Server with optimization flags
CMD="java -XX:+UseCompactObjectHeaders -jar jettraDBVM.jar"
echo "Starting server with: $CMD"
echo "Command executed: \`$CMD\`" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"
echo "\`\`\`" >> "$OUTPUT_FILE"

# Start in background
mkdir -p log
nohup $CMD > log/server_bench.log 2>&1 &
SERVER_PID=$!

# Wait for server startup
echo "Waiting for server to start (PID $SERVER_PID)..."
STARTED=false
for i in {1..30}; do
    if nc -z localhost 8080; then
        echo "Server is listening on port 8080."
        STARTED=true
        break
    fi
    sleep 1
done

if [ "$STARTED" = false ]; then
    echo "Error: Server failed to start."
    echo "Check log/server_bench.log for details."
    cat log/server_bench.log
    kill $SERVER_PID
    exit 1
fi

# Run benchmarks
echo "Running benchmark script..."
# Ensure permissions
chmod +x sh/testing/benchmark_all.sh

# Run and append output
./sh/testing/benchmark_all.sh 1000 >> "$OUTPUT_FILE"

echo "\`\`\`" >> "$OUTPUT_FILE"
echo "" >> "$OUTPUT_FILE"

# Cleanup
echo "Stopping server..."
kill $SERVER_PID
wait $SERVER_PID 2>/dev/null

echo "Benchmark complete. Results saved to $OUTPUT_FILE"
