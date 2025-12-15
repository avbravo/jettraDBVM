#!/bin/bash

# Configuration
URL="http://localhost:8080/api"
ADMIN_USER="admin"
ADMIN_PASS="admin"
USER_USER="testuser"
USER_PASS="testuser"

echo "=== Setup: Create test user ==="
curl -s -X POST "$URL/users" \
  -u "$ADMIN_USER:$ADMIN_PASS" \
  -d "{\"username\": \"$USER_USER\", \"password\": \"$USER_PASS\"}"
echo -e "\n"

echo "=== Setup: Create Database with specific engine ==="
DB_NAME="engine_test_db"
curl -s -X POST "$URL/dbs" \
  -u "$ADMIN_USER:$ADMIN_PASS" \
  -d "{\"name\": \"$DB_NAME\", \"engine\": \"JettraEngineStore\"}"
echo -e "\n"

echo "=== Test 1: Verify Engine Metadata (Admin) ==="
echo "Fetching /api/dbs as Admin..."
RESPONSE=$(curl -s "$URL/dbs" -u "$ADMIN_USER:$ADMIN_PASS")
echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "\"name\":\"$DB_NAME\"" && echo "$RESPONSE" | grep -q "\"engine\":\"JettraEngineStore\""; then
  echo "PASS: Admin sees correct engine type."
else
  echo "FAIL: Admin does not see correct engine type."
  exit 1
fi

echo "=== Test 2: Verify Engine Metadata (Non-Admin) ==="
# User needs access to db? Or is dbs list global?
# Currently list dbs gives all dbs.
echo "Fetching /api/dbs as User..."
RESPONSE=$(curl -s "$URL/dbs" -u "$USER_USER:$USER_PASS")
echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "\"name\":\"$DB_NAME\"" && echo "$RESPONSE" | grep -q "\"engine\":\"JettraEngineStore\""; then
  echo "PASS: User sees correct engine type."
else
  echo "FAIL: User does not see correct engine type."
fi

echo "=== Test 3: Verify System Collection Visibility (Admin) ==="
echo "Listing collections for $DB_NAME as Admin..."
RESPONSE=$(curl -s "$URL/dbs/$DB_NAME/cols" -u "$ADMIN_USER:$ADMIN_PASS")
echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "_engine" && echo "$RESPONSE" | grep -q "_info"; then
  echo "PASS: Admin sees system collections."
else
  echo "FAIL: Admin should see system collections."
  exit 1
fi

echo "=== Test 4: Verify System Collection Visibility (Non-Admin) ==="
echo "Listing collections for $DB_NAME as User..."
RESPONSE=$(curl -s "$URL/dbs/$DB_NAME/cols" -u "$USER_USER:$USER_PASS")
echo "Response: $RESPONSE"
if echo "$RESPONSE" | grep -q "_engine" || echo "$RESPONSE" | grep -q "_info"; then
  echo "FAIL: User SHOULD NOT see system collections."
  exit 1
else
  echo "PASS: User does not see system collections."
fi

echo "=== Cleanup ==="
# Delete DB
curl -s -X DELETE "$URL/dbs?name=$DB_NAME" -u "$ADMIN_USER:$ADMIN_PASS"
# Delete User
# Need to find ID first, but for now skip user cleanup or assume test enc
echo -e "\nDone."
