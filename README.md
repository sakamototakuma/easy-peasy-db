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
| `jdbc` | Embedded / Network 経由の JDBC インターフェース |
| `server` | DB 初期化・起動エントリポイント |

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

## Roadmap (WIP)

- [ ] SQL サポート範囲の拡張（集約/結合/最適化強化）
- [ ] 実行計画と統計情報の連携強化
- [ ] 同時実行制御と回復の検証ケース拡充
- [ ] ベンチマーク指標・レポート整備
- [ ] ドキュメント（設計ノート/図）の継続更新

---

## Acknowledgements

本プロジェクトは、Edward Sciore 著 *Database Design and Implementation* から着想を得ています。  
This repository is an independent educational implementation and is not affiliated with the author or publisher.

---

## Notes

- API や内部構造は、学習価値を高めるために今後も変更される可能性があります。
- 実験・検証用途での利用を前提としており、本番運用向けの保証は行っていません。