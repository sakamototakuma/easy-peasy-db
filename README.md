# EasyPeasyDB

Java で実装している、教育用の Database Engine です。  
JDBC クライアントから利用することを想定し、DBMS の内部実装をブラックボックスのまま扱うのではなく、ストレージ管理・インデックス・クエリ処理・トランザクション・復旧がどのように連携しているかを、実装しながら理解することを目的に開発しています。

> **Status:** Work in Progress  
> 本リポジトリは継続的に改良中です。機能追加・設計見直し・性能評価を並行して進めています。

---

## Motivation

普段アプリケーション開発で利用する RDBMS は非常に高機能ですが、その分、内部で何が起きているかは見えにくくなりがちです。  
このリポジトリでは、以下のような問いに実装ベースで向き合うことを目的にしています。

- レコードはどのようにページへ配置されるのか
- インデックスはどのように検索性能を支えるのか
- SQL はどのように解釈され、実行計画に変換されるのか
- トランザクションの整合性や同時実行制御はどう成立するのか
- 障害時にどのように復旧するのか

単に機能を再現することよりも、**責務分離と内部の相互作用を理解できる構成にすること**を重視しています。

---

## Current Features

現時点では、主に以下の機能を実装・検証しています。

- テーブル作成
- `insert / select / update / delete`
- `explain` 句（プラン木 + 実行時間の表示）
- 対話的 SQL 実行 (`SQLInterpreter` REPL)
- インデックス
- B+Tree ベースのデータ構造
- ページ管理
- SQL パーサ
- プランナ / 実行計画の生成
- トランザクション
- ロックベースの同時実行制御
- WAL / ログ
- 永続化
- リカバリ
- テスト
- ベンチマーク
  - クエリ性能
  - 更新性能
  - トランザクション遅延
  - block accessed などの観点での計測

---

## Architecture Overview

本実装は、DBMS の主要な責務をできるだけ分離して捉えられるように構成しています。

```text
SQL
 ↓
Parser
 ↓
Intermediate Representation (QueryData etc.)
 ↓
Planner
 ↓
Execution Plan
 ↓
Execution / Scan / Update
 ↓
Storage Manager / Buffer / Log / File
```

### Package Responsibilities

`easy-peasy-db/src/main/java/esypsydb` 配下は、責務ごとに以下のように分かれています。

| Package | Responsibility |
|---|---|
| `file` | ファイル・ページ I/O、ブロック単位アクセス |
| `buffer` | バッファ管理、pin/unpin、置換制御 |
| `log` | WAL ログの追記・走査 |
| `tx` | トランザクション管理、同時実行制御、回復 |
| `record` | レコードレイアウト、RID、テーブル走査 |
| `parse` | SQL 字句解析・構文解析 |
| `plan` | 論理/物理プラン構築、実行計画生成 |
| `query` | Scan 抽象、式/述語評価 |
| `metadata` | テーブル・統計・インデックスメタデータ |
| `index` | インデックス機能（B+Tree/Hash など） |
| `materialize` | 一時テーブル等の中間結果管理 |
| `multibuffer` | chunk分割、バッファを有効活用 |
| `opt` | クエリ最適化、Heuristicによるjoin order・Index選択 |
| `jdbc` | Embedded / Network 経由の JDBC インターフェース |
| `server` | DB 初期化・起動エントリポイント |
| `cli` | 対話的 SQL シェル (`SQLInterpreter`) |

---

## Storage Model

EasyPeasyDB は専用のブロックデバイスやカスタムファイルシステムを使わず、**ホストOSのファイルシステム上のファイル**をそのままバッキングストアとして利用します。

- DB ディレクトリ (接続URLで指定) 配下に、テーブル毎に1つのファイルが作成されます
- 各ファイルは `BLOCK_SIZE` バイトの固定長ブロックの配列として扱われます ([FileMgr.java](easy-peasy-db/src/main/java/esypsydb/file/FileMgr.java))
- `RandomAccessFile` を `"rws"` モードで開いているため、**書き込みは毎回 OS 経由でディスクに同期**されます (fsync相当)。耐久性優先で、スループットは控えめです
- 一時テーブル (`temp*`) は起動時に削除され、永続化対象外です

したがって I/O 性能は、動作しているマシンのディスク (SSD / HDD) 特性とファイルシステムに直接依存します。

---

## Configuration & Memory Footprint

| Parameter | Default | 意味 |
|---|---|---|
| `BLOCK_SIZE` | `4096` | 1ブロックのバイト数 (4 KiB) OSのページサイズ・FSブロックサイズに揃えることでI/Oを効率化 |
| `BUFFER_SIZE` | `1024` | バッファプールに常駐させるページ数 |
| `LOG_FILE` | `easypeasydb.log` | WAL ログファイル名 |

### Memory

- バッファプール使用量 = `BLOCK_SIZE × BUFFER_SIZE` = **4 KiB × 1024 = 4 MiB**
- ページは `ByteBuffer.allocateDirect()` で確保されるため、**JVM ヒープ外のダイレクトメモリ領域**に載ります ([Page.java](easy-peasy-db/src/main/java/esypsydb/file/Page.java))
- 上記に加え、ログバッファ・トランザクション状態・メタデータ等で追加のJVMヒープを使用します

---

## Getting Started

### Prerequisites

- JDK 21
- Maven 3.9+

> `pom.xml` の `maven-compiler-plugin` は `source/target = 21` です。

### Build & Test

```bash
cd easy-peasy-db
mvn test
```

### Site Documentation (optional)

```bash
cd easy-peasy-db
mvn site
```

---

## SQL Interpreter (REPL)

対話的にSQLを実行するためのREPLを `esypsydb.cli.SQLInterpreter` として提供しています。

### Quick Start

```bash
# 1. ビルド (初回 / コード変更時)
cd easy-peasy-db
mvn -q compile
cd ..

# 2. 起動 (リポジトリルートから)
./start-cli.sh                # studentdb を使用
./start-cli.sh mydb           # 任意の DB ディレクトリ名を指定可能
```

`start-cli.sh` は CWD をリポジトリルートに固定するため、DB ディレクトリは常に `Database-Implementation/<dbname>/` に作られます。指定したディレクトリが存在しなければ新規作成、存在すればリカバリして開きます。

> **注意:** スクリプトを使わず素の `java -cp ...` で起動する場合、DB ディレクトリは JVM の CWD 配下に作られます。`easy-peasy-db/` から起動すると `easy-peasy-db/studentdb/` ができてしまうため `start-cli.sh` の利用を推奨。

### 入力ルール

- 文の終端は **`;`**。終端が見つかるまで複数行入力できます (継続プロンプト ` ... `)
- 空行はスキップ
- メタコマンド (`;` 不要):
  - `help` または `?` … 使い方を表示
  - `exit` または `quit` … 終了

各文ごとに新しいトランザクションを開始し、エラー時は自動 rollback します。

### 対応している SQL

| 種別 | 例 |
|---|---|
| DDL | `create table t(id int, name varchar(10));`<br>`create index idx_id on t(id);`<br>`create view v as select id from t;` |
| DML | `insert into t(id, name) values(1, 'foo');`<br>`update t set name = 'bar' where id = 1;`<br>`delete from t where id = 1;` |
| Query | `select id, name from t where id = 1;` |
| Explain | `explain select id from t where id = 1;` |

### セッション例

```text
$ ./start-cli.sh
EasyPeasyDB SQL Interpreter (db=studentdb)
Statements end with ';'. Type 'help' for usage, 'exit' to quit.
SQL> create table t1(
 ... id int,
 ... name varchar(10)
 ... );
0 rows affected.
SQL> insert into t1(id, name) values(1, 'foo');
1 rows affected.
SQL> select id, name from t1;
id             name
------------------------------
1              foo
(1 rows)
SQL> explain select id from t1 where id = 1;
- ProjectPlan [blocks=1, rows=1]
  - SelectPlan [blocks=1, rows=1]
    - TablePlan [blocks=1, rows=1]
Execution time: 0.812 ms (actual rows=1)
SQL> exit
bye.
```

`explain` プレフィックスで `Planner.explainQuery` を呼び、プラン木のコスト見積もりと実測の実行時間を出力します

### スクリプト実行

標準入力リダイレクトでバッチ実行できます。

```bash
./start-cli.sh < schema.sql
```

### サンプルデータ

`samples/student/` のスキーマと生成スクリプトを使ってデータを投入できます。`data.sql` は `.gitignore` 対象（毎回ローカル生成）。

最短手順は `bench_load.sh`：プロファイルを指定するとビルド・データ生成・ロードまで一気に行います。

```bash
./bench_load.sh                  # bench-medium / dbname=benchdb
./bench_load.sh bench-small mybench
./bench_load.sh bench-large hugebench
```

| profile | student | enroll | 用途 |
|---|---:|---:|---|
| `smoke` | 5K | 5K | 動作確認 |
| `bench-small` | 50K | 100K | バッファプール内 |
| **`bench-medium`** | **200K** | **500K** | **推奨**: プール超え |
| `bench-large` | 500K | 2M | 本格ベンチ |

手動で行いたい場合：

```bash
./start-cli.sh demo < samples/student/schema.sql
python3 samples/student/gen_data.py --profile bench-medium    # または --students/--enrolls
./start-cli.sh demo < samples/student/data.sql
```

#### データ量の見積もり

スロットサイズは `4 (USED フラグ) + Σ field_bytes`.`int = 4`、`varchar(N) = 4 + N` (US-ASCII)。

```
records_per_block = floor(BLOCK_SIZE / slot_size)
table_blocks      = ceil(N / records_per_block)
table_bytes       = table_blocks × BLOCK_SIZE
```

`student` (slot=30B、136 rows/block) の例：200K 行 → 約 1471 block → **5.7 MiB**。バッファプール 4 MiB を超えるので I/O が発生し、index/multibuffer の挙動を観察できます。

---

## JDBC Notes (Current Implementation)

現状コードに基づく接続 URL 仕様は次の通りです。

- Embedded: `jdbc:esypsydb:<db-directory>`
- Network: `jdbc:easypeasydb://<host>`（RMI レジストリはコード上で `1099` 固定）

※ 起動・接続フローは今後見直しの可能性があります。

---

## Repository Structure

```text
Database-Implementation/
├── README.md
└── easy-peasy-db/
  ├── pom.xml
  ├── src/main/java/esypsydb/
  ├── src/test/java/
  └── src/site/
```
---

## License

MIT License. See [LICENSE](LICENSE).

---

## Acknowledgements

本プロジェクトは、Edward Sciore 著 *Database Design and Implementation* から着想を得ています。  
This repository is an independent educational implementation and is not affiliated with the author or publisher.

---

## Notes

- API や内部構造は、学習価値を高めるために今後も変更される可能性があります。
- 実験・検証用途での利用を前提としており、本番運用向けの保証は行っていません。