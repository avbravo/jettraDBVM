#!/bin/bash
cd "$(dirname "$0")/../.." || exit
HOST="http://localhost:8080"
AUTH="-u admin:adminadmin"
DB="adv_test_db"
OUT="books/guide/advanced_verification_output.txt"

mkdir -p books/guide

# Setup
echo "Setting up DB..."
curl -s $AUTH -X DELETE "$HOST/api/dbs?name=$DB"
curl -s $AUTH -X POST "$HOST/api/dbs" -d "{\"name\":\"$DB\", \"engine\":\"JettraBasicStore\"}"
curl -s $AUTH -X POST "$HOST/api/cols" -d "{\"database\":\"$DB\", \"collection\":\"users\"}"
curl -s $AUTH -X POST "$HOST/api/cols" -d "{\"database\":\"$DB\", \"collection\":\"orders\"}"

# Insert Data
echo "Inserting Data..."
curl -s $AUTH -X POST "$HOST/api/doc?db=$DB&col=users" -d '{"id":"u1", "name":"Alice", "address":{"city":"New York", "zip":"10001"}}'
curl -s $AUTH -X POST "$HOST/api/doc?db=$DB&col=users" -d '{"id":"u2", "name":"Bob", "address":{"city":"San Francisco", "zip":"94105"}}'
curl -s $AUTH -X POST "$HOST/api/doc?db=$DB&col=orders" -d '{"id":"o1", "user_id":"u1", "total":100}'
curl -s $AUTH -X POST "$HOST/api/doc?db=$DB&col=orders" -d '{"id":"o2", "user_id":"u1", "total":200}'
curl -s $AUTH -X POST "$HOST/api/doc?db=$DB&col=orders" -d '{"id":"o3", "user_id":"u2", "total":50}'

echo "--- Data Inserted ---" > $OUT

# 1. Embedded Documents Verification
echo "1. Verify Embedded (Alice in New York)" >> $OUT
# Using JQL FIND
echo "Querying embedded field (address.city = 'New York')..." >> $OUT
CMD='{"command":"FIND IN users WHERE address.city = \"New York\""}'
# Note: QueryExecutor currently supports top-level field check tokens[whereIdx+1]. 
# It likely doesn't support dot notation for nested fields yet in "executeJQL". 
# Let's test it.
curl -s $AUTH -X POST "$HOST/api/command?db=$DB" -d "$CMD" >> $OUT 2>&1
echo "" >> $OUT

# 2. Aggregations
echo "2. Verify Aggregation (Pipeline)" >> $OUT
CMD='{"command":"AGGREGATE IN orders PIPELINE [{\"group\": \"user_id\"}]"}'
curl -s $AUTH -X POST "$HOST/api/command?db=$DB" -d "$CMD" >> $OUT 2>&1
echo "" >> $OUT

# 3. Joins (SQL)
echo "3. Verify Join (SQL)" >> $OUT
CMD='{"command":"SELECT * FROM orders JOIN users ON orders.user_id = users.id"}'
echo "Running SQL Join..."
curl -s $AUTH -X POST "$HOST/api/command?db=$DB" -d "$CMD" >> $OUT 2>&1
echo "" >> $OUT

 echo "Verification Run Complete. Check $OUT"
