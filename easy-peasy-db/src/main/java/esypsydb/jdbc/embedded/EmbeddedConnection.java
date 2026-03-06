package esypsydb.jdbc.embedded;

import java.sql.SQLException;

import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;
import esypsydb.plan.Planner;
import esypsydb.jdbc.ConnectionAdapter;

/**
 * Connection の組み込み実装
 */

class EmbeddedConnection extends ConnectionAdapter {
    private EasyPeasyDB db;
    private Transaction currentTx;
    private Planner planner;

    /**
     * コネクションを作成し、新しいトランザクションを開始する
     */
    public EmbeddedConnection(EasyPeasyDB db) {
        this.db = db;
        currentTx = db.newTx();
        planner = db.planner();
    }

    /**
     * このコネクションの新しい Statement を作成する
     */
    public EmbeddedStatement createStatement() throws SQLException {
        return new EmbeddedStatement(this, planner);
    }

    /**
     * 現在のトランザクションをコミットしてコネクションを閉じる
     */
    public void close() throws SQLException {
        currentTx.commit();
    }

    /**
     * 現在のトランザクションをコミットし、新しいトランザクションを開始する
     */
    public void commit() throws SQLException {
        currentTx.commit();
        currentTx = db.newTx();

        // System.out.println(db.fileMgr().getStatistics().toString());
    }

    /**
     * 現在のトランザクションをロールバックし、新しいトランザクションを開始する
     */
    public void rollback() throws SQLException {
        currentTx.rollback();
        currentTx = db.newTx();

        // System.out.println(db.fileMgr().getStatistics().toString());
    }

    /**
     * このコネクションに紐づくトランザクションを返す
     * 非公開。他の JDBC クラスから呼ばれる内部メソッド
     *
     * @return このコネクションに紐づくトランザクション
     */
    Transaction getTransaction() {
        return currentTx;
    }
}

