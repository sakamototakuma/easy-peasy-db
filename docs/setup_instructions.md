# Setup & Run Guide

EasyPeasyDB をローカルでビルド・実行するための手順です。

---

## I. Prerequisites

| Tool | Version |
|---|---|
| JDK | 21 以上 |
| Maven | 3.9 以上 |

macOS (Homebrew 例):

```bash
brew install openjdk@21
brew install maven
```

---

## II. Build

プロジェクトのルートディレクトリから：

```bash
cd easy-peasy-db
mvn clean package
```

成功すると `easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar` (実行可能 fat jar) が生成されます。

---

## III. Running the Server

### 起動スクリプト経由 (推奨)

リポジトリのルートから：

```bash
chmod +x start-server.sh      # 初回のみ
./start-server.sh studentdb
```

`studentdb` はデータベースを保存するディレクトリ名です (存在しなければ自動作成)。

正常に起動すると以下が表示されます：

```
creating new database
new transaction: 1
transaction 1 committed
database server ready (db=studentdb, port=1099)
```

既存DBを指定した場合は `recovering existing database` となります。

ポートを変える場合は第2引数：

```bash
./start-server.sh studentdb 1100
```

### jar を直接叩く場合

```bash
java -jar easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar studentdb
```

---

## IV. Running Client Programs

> **Status: WIP** — JDBC / RMI クライアント層はまだ実装されていません。
> 下記は目標とする利用イメージで、現時点では [DriverAdapter.java](../easy-peasy-db/src/main/java/esypsydb/jdbc/DriverAdapter.java) がスタブのため動作しません。

### 目標とする利用フロー

```java
Driver d = new EasyPeasyDriver();
String url = "jdbc:easypeasydb://localhost";
Connection conn = d.connect(url, null);
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("select sname from STUDENT");
while (rs.next()) {
    System.out.println(rs.getString("sname"));
}
```

### 予定している付属クライアント (未実装)

- **SQLInterpreter** — 対話的にSQLを実行する REPL
- **CreateStudentDB** — サンプル学生DBを作成・投入
- **StudentMajors** — 学生と専攻を一覧

---

## V. Configuration

デフォルト値は [EasyPeasyDB.java](../easy-peasy-db/src/main/java/esypsydb/server/EasyPeasyDB.java)：

| Parameter | Default | 説明 |
|---|---|---|
| `BLOCK_SIZE` | `4096` | 1ブロック = 4 KiB |
| `BUFFER_SIZE` | `1024` | バッファプールのページ数 (総 4 MiB) |
| `LOG_FILE` | `easypeasydb.log` | WAL ログファイル名 |
| Port | `1099` | RMI レジストリ既定。起動時の第2引数で上書き可 |

---

## VI. Current Implementation Status

| Component | Status |
|---|---|
| ストレージ (file / buffer / log) | 実装済み |
| レコード / スキーマ / レイアウト | 実装済み |
| SQL パーサ / プランナ / 実行 | 実装済み |
| インデックス (B+Tree / Hash) | 実装済み |
| トランザクション / WAL / リカバリ | 実装済み |
| サーバー起動 (`StartUp`) | スケルトンのみ (RMI 未バインド) |
| JDBC ドライバ (クライアント側) | スタブ |
| RMI 経由のリモート接続 | 未実装 |
| 付属クライアント (SQLInterpreter 等) | 未実装 |

現時点では Embedded 利用 (Java コードから `EasyPeasyDB` を直接インスタンス化) が主な動作確認手段です。