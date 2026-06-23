# Setup & Run

## 必要環境

- JDK 21+ / Maven 3.9+

## ビルド

```bash
cd easy-peasy-db
mvn clean package -DskipTests   # → target/easy-peasy-db-1.0-SNAPSHOT.jar (fat jar)
```

## 対話シェル（CLI）で使う ← 一番簡単

```bash
./start-cli.sh           # studentdb を開く（無ければ作成、あればリカバリ）
./start-cli.sh mydb      # 任意の DB ディレクトリ名
```

DB は `<repo-root>/<dbname>/` に作られる。使い方は README の "SQL Interpreter (REPL)" 参照。

## サンプル DB（student）を作る

```bash
java -cp easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar \
     esypsydb.samples.CreateStudentDB studentdb

./bench_load.sh bench-medium studentdb   # 200K/500K 行
```s

## サーバ（RMI 経由・任意）

```bash
./start-server.sh studentdb [port]   # 既定 port=1099
```

`NetworkDriver` から `jdbc:easypeasydb://localhost` で接続
Embedded は `jdbc:esypsydb:<dir>`
