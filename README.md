# EasyPeasyDB

Java実装の教育用Database Engineです。  
JDBC クライアントから利用することを想定し、DBMS の内部実装をブラックボックスのまま扱うのではなく、ストレージ管理・インデックス・クエリ処理・トランザクション・復旧がどのように連携しているかを、実装しながら理解することを目的に開発しています。

---

## Current Features

- **SQL**: `create table` / `insert` / `select` / `update` / `delete`、`create index` / `create view`
- **クエリ最適化**: Selinger / Heuristic / Basic の 3 プランナ、Index / Hash / Merge / MultiBuffer の各 Join
- **解析ツール**: `explain [analyze]`（プラン木 + 推定/実測）、`compare`（プランナ横並び比較）、`indexcmp`（索引 ON/OFF 比較）
- **インデックス**: B+Tree / Extendible Hash
- **ストレージ/TX**: ページ・バッファ管理、WAL ログ、ロックベース同時実行制御、リカバリ、永続化

---

## Architecture Overview

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
| `index` | インデックス（B+Tree / Hash） |
| `materialize` | ソート・一時テーブル等の中間結果 |
| `multibuffer` | chunk 分割によるバッファ活用 |
| `opt` | クエリ最適化（join order / index 選択） |
| `jdbc` | Embedded / Network 経由の JDBC |
| `server` / `cli` | DB 起動エントリ / 対話シェル |

---

## Storage Model

- ホスト OS のファイルをバッキングストアに使用。テーブル毎に 1 ファイルで、各ファイルは `BLOCK_SIZE` の固定長ブロック配列（[FileMgr.java](easy-peasy-db/src/main/java/esypsydb/file/FileMgr.java)）。
- WAL ベースの耐久性: fsync はコミット時のログ flush のみ。セッション終了時に `checkpoint()` で dirty バッファをフラッシュし CHECKPOINT を記録 → 次回リカバリは checkpoint まで遡るだけで完了。
- 一時テーブル（`temp*`）は起動時に削除され、永続化対象外。

---

## Configuration

| Parameter | Default | 意味 |
|---|---|---|
| `BLOCK_SIZE` | `4096` | 1 ブロック = 4 KiB |
| `BUFFER_SIZE` | `1024` | バッファプールのページ数（計 4 MiB） |
| `LOG_FILE` | `easypeasydb.log` | WAL ログファイル名 |

ページは `ByteBuffer.allocateDirect()` で確保され、JVM ヒープ外のダイレクトメモリに載ります（[Page.java](easy-peasy-db/src/main/java/esypsydb/file/Page.java)）。

---

## JDBC Notes

- Embedded: `jdbc:esypsydb:<db-directory>`
- Network: `jdbc:easypeasydb://<host>`（RMI レジストリは `1099` 固定）

---

## License / Acknowledgements

MIT License（[LICENSE](LICENSE)）。
Edward Sciore 著 *Database Design and Implementation* から着想を得た独立した教育用実装で、著者・出版社とは無関係です。学習目的の実装であり、本番運用向けの保証はありません。
