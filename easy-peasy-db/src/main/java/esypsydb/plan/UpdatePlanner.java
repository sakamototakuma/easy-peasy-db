package esypsydb.plan;

import esypsydb.parse.*;
import esypsydb.tx.*;

public interface UpdatePlanner {
    /**
     * 指定されたINSERTステートメントを実行し、
     * 影響を受けたレコード数を返します。
     * @param data INSERTステートメントの解析表現
     * @param tx 呼び出し元のトランザクション
     * @return 影響を受けたレコード数
     */
    public int executeInsert(InsertData data, Transaction tx);
    
    /**
     * 指定されたDELETEステートメントを実行し、
     * 影響を受けたレコード数を返します。
     * @param data DELETEステートメントの解析表現
     * @param tx 呼び出し元のトランザクション
     * @return 影響を受けたレコード数
     */
    public int executeDelete(DeleteData data, Transaction tx);
    
    /**
     * 指定されたUPDATEステートメントを実行し、
     * 影響を受けたレコード数を返します。
     * @param data UPDATEステートメントの解析表現
     * @param tx 呼び出し元のトランザクション
     * @return 影響を受けたレコード数
     */
    public int executeModify(ModifyData data, Transaction tx);
    
    /**
     * 指定されたCREATE TABLEステートメントを実行し、
     * 影響を受けたレコード数を返します。
     * @param data CREATE TABLEステートメントの解析表現
     * @param tx 呼び出し元のトランザクション
     * @return 影響を受けたレコード数
     */
    public int executeCreateTable(CreateTableData data, Transaction tx);
    
    /**
     * 指定されたCREATE VIEWステートメントを実行し、
     * 影響を受けたレコード数を返します。
     * @param data CREATE VIEWステートメントの解析表現
     * @param tx 呼び出し元のトランザクション
     * @return 影響を受けたレコード数
     */
    public int executeCreateView(CreateViewData data, Transaction tx);
    
    /**
     * 指定されたCREATE INDEXステートメントを実行し、
     * 影響を受けたレコード数を返します。
     * @param data CREATE INDEXステートメントの解析表現
     * @param tx 呼び出し元のトランザクション
     * @return 影響を受けたレコード数
     */
    public int executeCreateIndex(CreateIndexData data, Transaction tx);
    }