#!/bin/sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJ="$SCRIPT_DIR/easy-peasy-db"
CLASSES="$PROJ/target/classes"
DEPS="$PROJ/target/dependency"

if [ ! -d "$CLASSES" ]; then
    echo "Compiling..." >&2
    (cd "$PROJ" && mvn -q compile)
fi

if [ ! -d "$DEPS" ]; then
    echo "Fetching dependencies..." >&2
    (cd "$PROJ" && mvn -q dependency:copy-dependencies -DincludeGroupIds=org.jline)
fi

DBNAME="${1:-studentdb}"
exec java -cp "$CLASSES:$DEPS/*" esypsydb.cli.SQLInterpreter "$DBNAME"
