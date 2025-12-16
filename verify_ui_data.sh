#!/bin/bash
echo "Logging in..."
TOKEN=$(curl -s -X POST http://localhost:8080/api/login -H "Content-Type: application/json" -d '{"username":"admin","password":"admin"}' | jq -r .token)
echo "Token: $TOKEN"

echo "Listing Databases:"
curl -s -H "Authorization: $TOKEN" http://localhost:8080/api/dbs

echo "Listing Invoices in almacenbasicdb:"
curl -s -H "Authorization: $TOKEN" "http://localhost:8080/api/query?db=almacenbasicdb&col=facturas&limit=5"
