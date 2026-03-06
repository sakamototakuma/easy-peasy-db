package esypsydb.jdbc.network;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import esypsydb.plan.Planner;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class RemoteConnectionImpl extends UnicastRemoteObject implements RemoteConnection {
    private EasyPeasyDB db;
    private Transaction currentTx;
    private Planner planner;

    /**
     * リモート接続を作成し、
     * そのための新しいトランザクションを開始します。
     *
     * @throws RemoteException
     */
    RemoteConnectionImpl(EasyPeasyDB db) throws RemoteException {
        this.db = db;
        currentTx = db.newTx();
        planner = db.planner();
    }

    /**
     * この接続用の新しいRemoteStatementを作成します。
     *
     */
    public RemoteStatement createStatement() throws RemoteException {
        return new RemoteStatementImpl(this, planner);
    }

    /**
     * 接続を閉じます。
     * 現在のトランザクションはコミットされます。
     *
     */
    public void close() throws RemoteException {
        currentTx.commit();
    }

// 以下のメソッドはサーバー側のクラスによって使用されます。

    /**
     * この接続に現在関連付けられているトランザクションを返します。
     *
     * @return この接続に関連付けられているトランザクション
     */
    Transaction getTransaction() {
        return currentTx;
    }

    /**
     * 現在のトランザクションをコミットし、
     * 新しいトランザクションを開始します。
     */
    void commit() {
        currentTx.commit();
        currentTx = db.newTx();
    }

    /**
     * 現在のトランザクションをロールバックし、
     * 新しいトランザクションを開始します。
     */
    void rollback() {
        currentTx.rollback();
        currentTx = db.newTx();
    }
}
