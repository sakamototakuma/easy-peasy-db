#!/bin/sh
#
# ベンチマーク用のサンプルデータを生成して新規DBにロードする。
#
# 使い方:
#   ./bench_load.sh                          # bench-medium / dbname=benchdb
#   ./bench_load.sh bench-small mybench
#   ./bench_load.sh bench-large hugebench
#
set -eu

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
PROFILE="${1:-bench-medium}"
DBNAME="${2:-benchdb}"

cd "$SCRIPT_DIR"

# 既存 DB があれば削除（再生成のため）
if [ -d "$DBNAME" ]; then
    echo "Removing existing DB: $DBNAME"
    rm -rf "$DBNAME"
fi

# 1. ビルド（必要なら）
if [ ! -d "easy-peasy-db/target/classes" ]; then
    echo "==> mvn compile"
    (cd easy-peasy-db && mvn -q compile)
fi

# 2. データ生成
echo "==> generating samples/student/data.sql (profile=$PROFILE)"
python3 samples/student/gen_data.py --profile "$PROFILE"
ls -lh samples/student/data.sql

# 3. スキーマ作成 → データロード
echo "==> loading schema"
./start-cli.sh "$DBNAME" < samples/student/schema.sql > /dev/null

echo "==> loading data (this can take a while; fsync per write)"
time ./start-cli.sh "$DBNAME" < samples/student/data.sql > /dev/null

echo "==> done. DB: $SCRIPT_DIR/$DBNAME"
echo "    試行例:  ./start-cli.sh $DBNAME"
