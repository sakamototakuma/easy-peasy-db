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
mvn clean package -DskipTests
```

テストをスキップしない場合は `-DskipTests` を外してください。

成功すると `easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar` (実行可能 fat jar) が生成されます。

---

## III. Running the Server

### 起動スクリプト経由

リポジトリのルートから：

```bash
chmod +x start-server.sh      # 初回のみ
./start-server.sh studentdb
```

`studentdb` はデータベースを保存するディレクトリ名です (存在しなければ自動作成)。

正常に起動すると以下が表示されます：

```text
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

## IV. The Student Sample Database

教材として [Sciore: *Database Design and Implementation*] 由来の大学/学生スキーマを採用しています。

### Schema

| Table | Columns |
|---|---|
| `dept` | `did int`, `dname varchar(8)` |
| `student` | `sid int`, `sname varchar(10)`, `majorid int`, `gradyear int` |
| `course` | `cid int`, `title varchar(20)`, `deptid int` |
| `sect` | `sectid int`, `courseid int`, `prof varchar(8)`, `yearoffered int` |
| `enroll` | `eid int`, `studentid int`, `sectionid int`, `grade varchar(2)` |

リレーション：`student.majorid → dept.did`、`course.deptid → dept.did`、`sect.courseid → course.cid`、`enroll.studentid → student.sid`、`enroll.sectionid → sect.sectid`。

> **命名の注意**: Lexer が `lowerCaseMode` で動くため、識別子は全て小文字で格納されます。また `section` は予約語衝突を避けるため `sect` に短縮しています。

### Creating the Sample Data

スキーマ定義とデータは外部化されており、以下に置いてあります：

```text
samples/student/
  schema.sql     # CREATE TABLE 定義
  data.sql      # INSERT 文 (データを増やすときはここに追記)
```

これを [CreateStudentDB.java](../easy-peasy-db/src/main/java/esypsydb/samples/CreateStudentDB.java) (Embedded モード) で読み込みます。サーバは起動不要 (同じDBファイルへのアクセスとなるため、むしろ止めてから実行してください)。

```bash
# リポジトリのルートから実行
java -cp easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar \
     esypsydb.samples.CreateStudentDB studentdb
```

スクリプトディレクトリを差し替えたい場合は第2引数で指定：

```bash
java -cp easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar \
     esypsydb.samples.CreateStudentDB studentdb samples/student-large
```

内部では [SqlScriptRunner.java](../easy-peasy-db/src/main/java/esypsydb/samples/SqlScriptRunner.java) が汎用的に `.sql` ファイルを読み込んで1トランザクションで実行するため、別ドメインのサンプルDB (例: `samples/library/`) を追加するのも同じパターンで対応できます。

### SQL スクリプトの制約

- ステートメント区切りは `;`
- `--` から行末まではコメントとして無視
- 文字列リテラル内の `;` や `--` は未対応 (エスケープが必要になる程度の大量データは CSV インポーターを別途用意する想定)
- Parser の制約上、識別子は小文字、比較は `=` のみ、`*` / `JOIN` / サブクエリ / 集約 なし

### Sample Queries (SimpleDB SQL サブセット)

```sql
-- 学生名と専攻名の一覧 (joinは from句の複数テーブル + where句の等価結合で表現)
select sname, dname from student, dept where majorid = did

-- 特定学科の学生
select sname, gradyear from student, dept
where majorid = did and dname = 'compsci'

-- 3テーブルjoin: 学生の履修科目タイトル
select sname, title from student, enroll, sect, course
where sid = studentid and sectionid = sectid and courseid = cid
```

---

## V. Running Client Programs

### Embedded モード (サーバ不要)

`EmbeddedDriver` を使えばサーバを立てずに直接 DB ファイルへアクセスできます。`CreateStudentDB` はその典型例です (セクション IV 参照)。

### Network モード (RMI 経由)

サーバを起動した状態で `NetworkDriver` を使って接続します。

```java
import java.sql.*;
import esypsydb.jdbc.network.NetworkDriver;

Driver d = new NetworkDriver();
String url = "jdbc:easypeasydb://localhost";
Connection conn = d.connect(url, null);
Statement stmt = conn.createStatement();
ResultSet rs = stmt.executeQuery("select sname from student");
while (rs.next()) {
    System.out.println(rs.getString("sname"));
}
rs.close();
conn.close();
```

コンパイル・実行時は fat jar をクラスパスに追加：

```bash
javac -cp easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar MyClient.java
java  -cp easy-peasy-db/target/easy-peasy-db-1.0-SNAPSHOT.jar:. MyClient
```

### 対話クライアント

- **SQLInterpreter** (`esypsydb.cli.SQLInterpreter`) — 対話的に SQL を実行する REPL。リポジトリルートの `start-cli.sh` から起動。詳細は README の "SQL Interpreter (REPL)" セクション参照。

以下は今後追加予定：

- **StudentMajors** — 学生と専攻名を一覧
- **FindMajors** — 指定学科の学生を検索
- **ChangeMajor** — 学生の専攻を更新

---

## VI. Configuration

デフォルト値は [EasyPeasyDB.java](../easy-peasy-db/src/main/java/esypsydb/server/EasyPeasyDB.java)：

| Parameter | Default | 説明 |
| --- | --- | --- |
| `BLOCK_SIZE` | `4096` | 1ブロック = 4 KiB |
| `BUFFER_SIZE` | `1024` | バッファプールのページ数 (総 4 MiB) |
| `LOG_FILE` | `easypeasydb.log` | WAL ログファイル名 |
| Port | `1099` | RMI レジストリ既定。起動時の第2引数で上書き可 |

---

## VII. Current Implementation Status

| Component | Status |
| --- | --- |
| ストレージ (file / buffer / log) | 実装済み |
| レコード / スキーマ / レイアウト | 実装済み |
| SQL パーサ / プランナ / 実行 | 実装済み |
| インデックス (B+Tree / Hash) | 実装済み |
| トランザクション / WAL / リカバリ | 実装済み |
| サーバー起動 (`StartUp`) | 実装済み (RMI バインド済み) |
| Embedded サンプル (`CreateStudentDB`) | 実装済み |
| JDBC ドライバ — Embedded / Network | 実装済み |
| RMI 経由のリモート接続 | 実装済み (`NetworkDriver`) |
| 対話クライアント (`SQLInterpreter`) | 実装済み (`esypsydb.cli.SQLInterpreter`) |