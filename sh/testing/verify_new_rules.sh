#!/bin/bash
cd "$(dirname "$0")/../.." || exit
HOST="http://localhost:8080"
AUTH="-u admin:adminadmin"

# Function to check if previous command failed
check_error() {
  if [ $? -ne 0 ]; then
    echo "Error executing command"
    exit 1
  fi
}

echo "=== JettraDB Validation Rules Verification ==="

# 1. Create DB and Collections
echo "Step 1: Creating database 'testvalidation'..."
curl -s $AUTH -X POST "$HOST/api/dbs" -d '{"name":"testvalidation"}'
echo ""

echo "Step 2: Creating collections..."
curl -s $AUTH -X POST "$HOST/api/cols" -d '{"database":"testvalidation", "collection":"_rules"}'
echo ""
curl -s $AUTH -X POST "$HOST/api/cols" -d '{"database":"testvalidation", "collection":"pais"}'
echo ""
curl -s $AUTH -X POST "$HOST/api/cols" -d '{"database":"testvalidation", "collection":"persona"}'
echo ""

# 2. Insert Rules
echo "Step 3: Inserting rules..."
RULES_DOC='{
  "persona": [
    {
      "pais": {
        "collectionreferenced": "pais",
        "externalfield": "_id",
        "type": "referenced"
      }
    },
    {
      "nombre": {
        "type": "validation",
        "value": "notnull"
      }
    },
    {
      "edad": {
        "type": "min_value",
        "value": 18
      }
    }
  ],
  "pais": [
     {
       "codigo": { "type":"validation", "value":"notnull" }
     }
  ]
}'
curl -s $AUTH -X POST "$HOST/api/doc?db=testvalidation&col=_rules" -d "$RULES_DOC"
echo ""

# 3. Insert Reference Data (Pais)
echo "Step 4: Inserting Reference Data (Pais)..."
# We need to extract the ID. Assuming response is {"id":"..."}
PAIS_RESP=$(curl -s $AUTH -X POST "$HOST/api/doc?db=testvalidation&col=pais" -d '{"codigo":"PA", "nombre":"Panama"}')
echo "Response: $PAIS_RESP"
PAIS_ID=$(echo $PAIS_RESP | grep -o '"id":"[^"]*' | cut -d'"' -f4)
echo "Pais ID: $PAIS_ID"

if [ -z "$PAIS_ID" ]; then
    echo "Failed to create Pais"
    exit 1
fi

# 4. Insert Valid Persona
echo "Step 5: Inserting Valid Persona..."
curl -s $AUTH -X POST "$HOST/api/doc?db=testvalidation&col=persona" -d "{\"nombre\":\"Juan\", \"edad\":20, \"pais\":{\"_id\":\"$PAIS_ID\"}}"
echo ""

# 5. Insert Invalid Persona (Null Name)
echo "Step 6: Inserting Invalid Persona (Null Name) - Should Fail..."
curl -s $AUTH -X POST "$HOST/api/doc?db=testvalidation&col=persona" -d "{\"edad\":20, \"pais\":{\"_id\":\"$PAIS_ID\"}}"
echo ""

# 6. Insert Invalid Persona (Underage)
echo "Step 7: Inserting Invalid Persona (Underage age 10 < 18) - Should Fail..."
curl -s $AUTH -X POST "$HOST/api/doc?db=testvalidation&col=persona" -d "{\"nombre\":\"Pedro\", \"edad\":10, \"pais\":{\"_id\":\"$PAIS_ID\"}}"
echo ""

# 7. Insert Invalid Persona (Bad Reference)
echo "Step 8: Inserting Invalid Persona (Bad Reference) - Should Fail..."
curl -s $AUTH -X POST "$HOST/api/doc?db=testvalidation&col=persona" -d "{\"nombre\":\"Luis\", \"edad\":25, \"pais\":{\"_id\":\"bad_id\"}}"
echo ""

echo "Verification Complete."
