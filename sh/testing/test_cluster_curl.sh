#!/bin/bash
cd "$(dirname "$0")/../.." || exit
# test_cluster_curl.sh
# Checks cluster status via curl at intervals to observe leader flapping.

URL=${1:-"http://localhost:8080"}
USER_PASS="admin:admin" # Using common default from bash scripts

echo "Monitoring cluster status at $URL... (Ctrl+C to stop)"
echo "Time | Leader ID | State | Term"
while true; do
  STATUS=$(curl -s -u $USER_PASS $URL/api/cluster)
  if [ $? -eq 0 ]; then
    LEADER=$(echo $STATUS | jq -r '.leaderId')
    STATE=$(echo $STATUS | jq -r '.state')
    TERM=$(echo $STATUS | jq -r '.term')
    TIME=$(date +"%H:%M:%S")
    echo "$TIME | $LEADER | $STATE | $TERM"
  else
    echo "$(date +"%H:%M:%S") | FAILED TO CONNECT"
  fi
  sleep 1
done
