#!/bin/sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
CLASSES="$SCRIPT_DIR/easy-peasy-db/target/classes"

if [ ! -d "$CLASSES" ]; then
    echo "Classes not found: $CLASSES" >&2
    echo "Run 'cd easy-peasy-db && mvn -q compile' first." >&2
    exit 1
fi

cd "$SCRIPT_DIR"
DBNAME="${1:-studentdb}"
exec java -cp "$CLASSES" esypsydb.cli.SQLInterpreter "$DBNAME"
