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

`explain` プレフィックスで `Planner.explainQuery` を呼び、プラン木のコスト見積もりと実測の実行時間を出力します。

### スクリプト実行

標準入力リダイレクトでバッチ実行できます。

```bash
./start-cli.sh < schema.sql
```

### サンプルデータ

`samples/student/` にスキーマとデータ生成スクリプトがあります。`data.sql` は `.gitignore` 対象なので、ローカルで生成して使います。

```bash
# 1. スキーマ作成 (必要なら新しい DB ディレクトリで)
./start-cli.sh demo < samples/student/schema.sql

# 2. data.sql を生成 (既定: student/enroll 各 5000 行)
python3 samples/student/gen_data.py

# 3. ロード
./start-cli.sh demo < samples/student/data.sql
```

行数を変えたい場合：

```bash
python3 samples/student/gen_data.py --students 100000 --enrolls 200000 --seed 42
```

| 目的 | 推奨件数 |
|---|---|
| 動作確認・EXPLAIN の確認 | 5K (既定) |
| index による blocks 削減を実測 | student 50K-100K + enroll 100K |
| multibuffer join の chunk 分割を観察 | 両側 100K + `BUFFER_SIZE` を一時的に小さく (例 8) |
| ベンチマーク的比較 | 500K-1M |

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

## Acknowledgements

本プロジェクトは、Edward Sciore 著 *Database Design and Implementation* から着想を得ています。  
This repository is an independent educational implementation and is not affiliated with the author or publisher.

---

## Notes

- API や内部構造は、学習価値を高めるために今後も変更される可能性があります。
- 実験・検証用途での利用を前提としており、本番運用向けの保証は行っていません。