#!/bin/sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$SCRIPT_DIR/easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar"

if [ ! -f "$JAR" ]; then
    echo "Jar not found: $JAR" >&2
    echo "Run 'cd easy-peasy-db && mvn clean package' first." >&2
    exit 1
fi

exec java -jar "$JAR" "$@"