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

# 2. データ生成 (ルートに一時生成、ロード後削除)
DATA_SQL="$SCRIPT_DIR/data.sql"
echo "==> generating data.sql (profile=$PROFILE)"
python3 samples/student/gen_data.py --profile "$PROFILE" --out "$DATA_SQL"
ls -lh "$DATA_SQL"

# 3. スキーマ作成 → データロード → 一時ファイル削除
echo "==> loading schema"
./start-cli.sh "$DBNAME" < samples/student/schema.sql > /dev/null

echo "==> loading data (this can take a while; fsync per write)"
time ./start-cli.sh "$DBNAME" < "$DATA_SQL" > /dev/null

rm -f "$DATA_SQL"

echo "==> done. DB: $SCRIPT_DIR/$DBNAME"
echo "    試行例:  ./start-cli.sh $DBNAME"
