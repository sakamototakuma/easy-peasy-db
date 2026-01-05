package esypsydb.tx.recovery;

import esypsydb.log.LogMgr;
import esypsydb.server.EasyPeasyDB;
import esypsydb.file.Page;

public interface LogRecord {
    static final int CHECKPOINT = 0, START = 1, COMMIT = 2,
                     ROLLBACK = 3, SETINT = 4, SETSTRING = 5;
                    
    static final LogMgr logMgr = EasyPeasyDB.logMgr();
    
    // レコードログのオペコード                 
    int op();

    // トランザクション番号
    int txNumber();

    // 
    void undo(int txnum);

    /**
     *  ログにレコードを書き込み、そのLSNを返す
     * @return the log record in the log
     */ 
    int writeToLog();
    
}
