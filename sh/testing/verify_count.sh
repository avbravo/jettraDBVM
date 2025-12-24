#!/bin/bash
cd "$(dirname "$0")/../.." || exit
HOST="http://localhost:8080"
AUTH="-u admin:adminadmin"
DB="test_count_db"
COL="count_col"

echo "Creating DB and Collection..."
curl -s $AUTH -X POST "$HOST/api/dbs" -d "{\"name\":\"$DB\", \"engine\":\"JettraBasicStore\"}"
curl -s $AUTH -X POST "$HOST/api/cols" -d "{\"database\":\"$DB\", \"collection\":\"$COL\"}"

echo "Inserting 5 documents..."
for i in {1..5}; do
   curl -s $AUTH -X POST "$HOST/api/doc?db=$DB&col=$COL" -d "{\"val\":$i}" > /dev/null
done

echo "Verifying Count API..."
RESPONSE=$(curl -s $AUTH "$HOST/api/count?db=$DB&col=$COL")
echo "Raw Response: $RESPONSE"

COUNT=$(echo "$RESPONSE" | jq .count)
echo "Count Result: $COUNT"

if [ "$COUNT" == "5" ]; then
    echo "SUCCESS: Count is 5"
else
    echo "FAILURE: Count is $COUNT"
fi

# Cleanup
curl -s $AUTH -X DELETE "$HOST/api/dbs?name=$DB"
