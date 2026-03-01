package esypsydb.tx.recovery;

import esypsydb.file.*;
import esypsydb.tx.Transaction;

public interface LogRecord {
    static final int CHECKPOINT = 0, START = 1, COMMIT = 2,
                     ROLLBACK = 3, SETINT = 4, SETSTRING = 5;
                    
                   
    int op();
    int txNumber();
    void undo(Transaction tx);

    /**
     * bytesの先頭レコードを見て、正しいクラスに振り分けるファクトリ
     *
     * @param bytes
     * @return
     */
    static LogRecord createLogRecord(byte[] bytes) {
        Page p = new Page(bytes);
        switch (p.getInt(0)) {
            case CHECKPOINT:
                return new CheckpointRecord();
            case START:
                return new StartRecord(p);
            case COMMIT:
                return new CommitRecord(p);
            case ROLLBACK:
                return new RollbackRecord(p);
            case SETINT:
                return new SetIntRecord(p);
            case SETSTRING:
                return new SetStringRecord(p);
            // case NQCKPT:
            //     return new NQCheckpointRecord(p);
            default:
                return null;
        }
    }
}
