#!/bin/bash
cd "$(dirname "$0")/../.." || exit

JAR="jettra-federated-shell/target/jettraFederatedShell.jar"

if [ ! -f "$JAR" ]; then
    echo "JAR not found. Building..."
    mvn package -pl jettra-federated-shell -DskipTests
fi

java -jar "$JAR" "$@"
