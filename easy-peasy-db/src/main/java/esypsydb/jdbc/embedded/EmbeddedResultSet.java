package esypsydb.jdbc.embedded;

import java.sql.*;
import esypsydb.record.Schema;
import esypsydb.query.Scan;
import esypsydb.plan.Plan;
import esypsydb.jdbc.ResultSetAdapter;

/**
 * ResultSet の組み込み実装
 */
public class EmbeddedResultSet extends ResultSetAdapter {
   private Scan s;
   private Schema sch;
   private EmbeddedConnection conn;

   /**
    * 指定されたプランから Scan オブジェクトを作成する
    * @param plan クエリプラン
    * @param conn コネクション
    */
   public EmbeddedResultSet(Plan plan, EmbeddedConnection conn) throws SQLException {
      s = plan.open();
      sch = plan.schema();
      this.conn = conn;
   }

   /**
    * 保持しているスキャンを進めて、結果セットの次のレコードに移動する
    */
   public boolean next() throws SQLException {
      try {
         return s.next();
      }
      catch(RuntimeException e) {
         conn.rollback();
         throw new SQLException(e);
      }
   }

   /**
    * 指定されたフィールドの整数値をスキャンから取得して返す
    */
   public int getInt(String fldname) throws SQLException {
      try {
         fldname = fldname.toLowerCase(); // 大文字・小文字を区別しないよう小文字化
         return s.getInt(fldname);
      }
      catch(RuntimeException e) {
         conn.rollback();
         throw new SQLException(e);
      }
   }

   /**
    * 指定されたフィールドの文字列値をスキャンから取得して返す
    */
   public String getString(String fldname) throws SQLException {
      try {
         fldname = fldname.toLowerCase(); // 大文字・小文字を区別しないよう小文字化
         return s.getString(fldname);
      }
      catch(RuntimeException e) {
         conn.rollback();
         throw new SQLException(e);
      }
   }

   /**
    * スキーマを EmbeddedMetaData に渡して結果セットのメタデータを返す
    */
   public ResultSetMetaData getMetaData() throws SQLException {
      return new EmbeddedMetaData(sch);
   }

   /**
    * スキャンを閉じてコミットし、結果セットをクローズする
    */
   public void close() throws SQLException {
      s.close();
      conn.commit();
   }
}

