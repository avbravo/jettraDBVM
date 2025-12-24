#!/bin/bash
cd "$(dirname "$0")/../.." || exit
HOST="http://localhost:8080"
AUTH="-u admin:adminadmin"
RESULT_FILE="books/guide/result.md"

mkdir -p books/guide

echo "# JettraDB Benchmark Report" > $RESULT_FILE
echo "" >> $RESULT_FILE
echo "## Configuration" >> $RESULT_FILE
echo "- Date: $(date)" >> $RESULT_FILE
echo "- Host: $HOST" >> $RESULT_FILE
echo "" >> $RESULT_FILE

# Function to benchmark a database
benchmark_db() {
    DB_NAME=$1
    ENGINE=$2
    COL="benchmark_col"
    
    echo "--------------------------------------------------"
    echo "Benchmarking DB: $DB_NAME (Engine: $ENGINE)"
    echo "--------------------------------------------------"
    
    echo "### Database: $DB_NAME (Engine: $ENGINE)" >> $RESULT_FILE
    
    # 1. Create DB
    echo "Creating DB..."
    curl -s $AUTH -X POST "$HOST/api/dbs" -d "{\"name\":\"$DB_NAME\", \"engine\":\"$ENGINE\"}"
    
    # 2. Create Collection
    echo "Creating Collection..."
    curl -s $AUTH -X POST "$HOST/api/cols" -d "{\"database\":\"$DB_NAME\", \"collection\":\"$COL\"}"
    
    # 3. Insert 1000 Documents
    echo "Inserting 1000 documents..."
    START=$(date +%s%N)
    for i in {1..1000}
    do
       DOC="{\"name\":\"User_$i\", \"age\":$i, \"data\":\"Some random data to fluff up the size...\"}"
       curl -s $AUTH -X POST "$HOST/api/doc?db=$DB_NAME&col=$COL" -d "$DOC" > /dev/null
    done
    END=$(date +%s%N)
    DURATION=$((($END - $START)/1000000))
    echo "Insert Time: ${DURATION}ms"
    echo "- **Insert Time (1000 docs):** ${DURATION}ms" >> $RESULT_FILE
    
    # 4. Read (Find All) - Serialization/Deserialization test
    echo "Reading documents..."
    START=$(date +%s%N)
    curl -s $AUTH "$HOST/api/query?db=$DB_NAME&col=$COL&limit=1000" > /dev/null
    END=$(date +%s%N)
    DURATION=$((($END - $START)/1000000))
    echo "Read Time (All): ${DURATION}ms"
    echo "- **Read Time (1000 docs):** ${DURATION}ms" >> $RESULT_FILE
    
    # 5. Storage Size
    SIZE=$(du -sh "data/$DB_NAME" | cut -f1)
    echo "Storage Size: $SIZE"
    echo "- **Storage Size:** $SIZE" >> $RESULT_FILE
    
    # 6. Delete DB (Cleanup)
    # curl -s $AUTH -X DELETE "$HOST/api/dbs?name=$DB_NAME"
    echo "" >> $RESULT_FILE
}

# Run Benchmarks
benchmark_db "almacenbasicdb" "JettraBasicStore"
benchmark_db "almacenstoreenginedb" "JettraEngineStore"

echo "Benchmark Complete. Results saved to $RESULT_FILE"
cat $RESULT_FILE
