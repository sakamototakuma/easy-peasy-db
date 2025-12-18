# SimpleDB 環境構築ガイド

このドキュメントでは、SimpleDBの環境構築、ビルド、および実行手順について説明します。

## 前提条件

* **Java Development Kit (JDK) 21**
  * Java 21がインストールされていることを確認してください。
  * 確認コマンド: `java -version`
* **Apache Maven**
  * プロジェクトのビルドにMavenを使用します。
  * 確認コマンド: `mvn -version`

### MacOSでのインストール例 (Homebrew使用)

```bash
brew install openjdk@21
brew install maven
```

※ JDKのパス設定が必要な場合があります。

## プロジェクトのビルド

1. ターミナル（またはコマンドプロンプト）を開き、プロジェクトのルートディレクトリ（`pom.xml` がある場所）に移動します。

2. 以下のコマンドを実行して、プロジェクトをビルドします。

   ```bash
   mvn clean package
   ```

3. ビルドが成功すると、`server/target` ディレクトリに `server-2.10-SNAPSHOT.jar` が生成されます。

## サーバーの起動

SimpleDBサーバーを起動するには、以下の手順を実行します。

### MacOS / Linux でのサーバー起動

付属のシェルスクリプトを使用します。

1. スクリプトに実行権限を付与します（初回のみ）。

   ```bash
   chmod +x start-server.sh
   ```

2. サーバーを起動します。引数にはデータベースディレクトリ名を指定します（例: `studentdb`）。

   ```bash
   ./start-server.sh studentdb
   ```

### Windows でのサーバー起動

付属のバッチファイルを使用します。

1. コマンドプロンプトで以下のコマンドを実行します。

   ```cmd
   start-server.bat studentdb
   ```

サーバーが正常に起動すると、以下のようなログが表示されます。

```text
creating new database
new transaction: 1
transaction 1 committed
database server ready
```

## クライアントプログラムの実行

サーバーが起動している状態で、別のターミナルウィンドウを開き、クライアントプログラムを実行します。

### MacOS / Linux でのクライアント実行

クラスパスに生成されたJARファイルとテストクラスを含めて実行します。

#### 例: 学生データベースの作成 (CreateStudentDB)

```bash
java -cp server/target/server-2.10-SNAPSHOT.jar:server/target/test-classes simpledb.remote.CreateStudentDB
```

#### 例: SQLインタプリタ (SQLInterpreter)

```bash
java -cp server/target/server-2.10-SNAPSHOT.jar:server/target/test-classes simpledb.remote.SQLInterpreter
```

## 主なクライアントプログラム

* `simpledb.remote.CreateStudentDB`: 学生データベースを作成し、初期データを投入します。
* `simpledb.remote.StudentMajors`: 学生と専攻のリストを表示します。
* `simpledb.remote.FindMajors`: 引数で指定した学科の学生を表示します。
* `simpledb.remote.SQLInterpreter`: 対話形式でSQLを実行します。
* `simpledb.remote.ChangeMajor`: データ更新のサンプルです。

## トラブルシューティング

* **`mvn` コマンドが見つからない**: Mavenがインストールされていないか、PATHが通っていません。
* **Javaのバージョンエラー**: `java -version` でバージョンが21であることを確認してください。`pom.xml` はJava 21を要求するように設定されています。
* **ポート競合**: サーバーはデフォルトでポート1099を使用します。すでに使用されている場合は起動に失敗する可能性があります。
