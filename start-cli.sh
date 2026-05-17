#!/bin/sh
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROJ="$SCRIPT_DIR/easy-peasy-db"
CLASSES="$PROJ/target/classes"
DEPS="$PROJ/target/dependency"

(cd "$PROJ" && mvn -q compile)

if [ ! -d "$DEPS" ]; then
    echo "Fetching dependencies..." >&2
    (cd "$PROJ" && mvn -q dependency:copy-dependencies -DincludeGroupIds=org.jline)
fi

DBNAME="${1:-studentdb}"
exec java -cp "$CLASSES:$DEPS/*" esypsydb.cli.SQLInterpreter "$DBNAME"
