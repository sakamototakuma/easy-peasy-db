package esypsydb.jdbc.embedded;

import java.sql.SQLException;
import esypsydb.tx.Transaction;
import esypsydb.plan.*;
import esypsydb.jdbc.StatementAdapter;

/**
 * Statement の組み込み実装
 */
class EmbeddedStatement extends StatementAdapter {
   private EmbeddedConnection conn;
   private Planner planner;
   
   public EmbeddedStatement(EmbeddedConnection conn, Planner planner) {
      this.conn = conn;
      this.planner = planner;
   }
   
   /**
    * 指定された SQL クエリ文字列を実行する
    * クエリプランナーでプランを生成し、ResultSet に渡して処理する
    * プラン生成に失敗した場合はロールバックして SQLException をスローする
    */
   public EmbeddedResultSet executeQuery(String qry) throws SQLException {
      try {
         Transaction tx = conn.getTransaction();
         Plan pln = planner.createQueryPlan(qry, tx);
         return new EmbeddedResultSet(pln, conn);
      }
      catch(RuntimeException e) {
         conn.rollback();
         throw new SQLException(e);
      }
   }
   
   /**
    * 指定された SQL 更新コマンドを実行する
    * 更新プランナーに渡してコミットする
    * エラー時はロールバックして SQLException をスローする
    */
   public int executeUpdate(String cmd) throws SQLException {
      try {
         Transaction tx = conn.getTransaction();
         int result = planner.executeUpdate(cmd, tx);
         conn.commit();
         return result;
      }
      catch(RuntimeException e) {
         conn.rollback();
         throw new SQLException(e);
      }
   }
   
   public void close() throws SQLException {
   }
}
