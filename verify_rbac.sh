#!/bin/bash

# Start server in background
java -jar jettra-server/target/jettraDBVM.jar > server_output_rbac.log 2>&1 &
SERVER_PID=$!
echo "Server started with PID $SERVER_PID"
sleep 10

# Helper to call API
# Usage: call_api <method> <endpoint> <auth_user:pass> [data]
call_api() {
    METHOD=$1
    ENDPOINT=$2
    AUTH=$3
    DATA=$4
    
    if [ -z "$DATA" ]; then
        curl -s -X $METHOD "http://localhost:8080$ENDPOINT" -u "$AUTH"
    else
        curl -s -X $METHOD "http://localhost:8080$ENDPOINT" -u "$AUTH" -H "Content-Type: application/json" -d "$DATA"
    fi
}

# 1. Login as admin acts as setup (create DBs)
echo "Setting up databases..."
call_api POST "/api/dbs" "admin:admin" '{"name": "restricted_db", "engine": "JettraBasicStore"}'
call_api POST "/api/dbs" "admin:admin" '{"name": "admin_only_db", "engine": "JettraBasicStore"}'

# 2. Create restricted user
echo "Creating restricted user..."
call_api POST "/api/users" "admin:admin" '{"username": "user1", "password": "user1", "role": "reader", "allowed_dbs": ["restricted_db"]}'

# 3. List DBs as Admin
echo "Listing DBs as Admin..."
ADMIN_LIST=$(call_api GET "/api/dbs" "admin:admin")
echo $ADMIN_LIST

if echo "$ADMIN_LIST" | grep -q "admin_only_db" && echo "$ADMIN_LIST" | grep -q "restricted_db"; then
    echo "VERIFICATION SUCCESS: Admin sees all DBs."
else
    echo "VERIFICATION FAILED: Admin missing DBs."
fi

# 4. List DBs as user1
echo "Listing DBs as user1..."
USER_LIST=$(call_api GET "/api/dbs" "user1:user1")
echo $USER_LIST

if echo "$USER_LIST" | grep -q "restricted_db"; then
    echo "VERIFICATION SUCCESS: User1 sees allowed DB."
else
    echo "VERIFICATION FAILED: User1 missing allowed DB."
fi

if echo "$USER_LIST" | grep -q "admin_only_db"; then
    echo "VERIFICATION FAILED: User1 sees forbidden DB (admin_only_db)."
else
    echo "VERIFICATION SUCCESS: User1 does NOT see forbidden DB."
fi

# Kill server
kill $SERVER_PID
echo "--- Server Log ---"
cat server_output_rbac.log
echo "--- Data Dir ---"
ls -F data/
