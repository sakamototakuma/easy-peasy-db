package esypsydb.tx.recovery;

import java.util.*;

import esypsydb.file.*;
import esypsydb.buffer.Buffer;
import esypsydb.buffer.BufferMgr;
import esypsydb.log.*;
import esypsydb.tx.Transaction;

import static esypsydb.tx.recovery.LogRecord.*;

// リカバリマネージャ. 各TXが専用に1つ持つ
public class RecoveryMgr {
    private LogMgr lm;
    private BufferMgr bm;
    private Transaction tx;
    private int txnum;


    /**
     * リカバリマネージャ作成
     * 
     * @param txnum
     */
    public RecoveryMgr(Transaction tx, int txnum, LogMgr lm, BufferMgr bm) {
        this.tx = tx;
        this.txnum = txnum;
        this.lm = lm;
        this.bm = bm;
        StartRecord.writeToLog(lm, txnum);
    }

    /**
     * コミットレコードへの書き込みとflush
     */
    public void commit() {
        bm.flushAll(txnum);   // undo-only
        int lsn = CommitRecord.writeToLog(lm, txnum);
        lm.flush(lsn);
    }

    /**
     * ロールバックレコードへの書き込みとflush
     */
    public void rollback() {
        doRollback();
        bm.flushAll(txnum);
        int lsn = RollbackRecord.writeToLog(lm, txnum);
        lm.flush(lsn);
    }

    /**
     * 未完了のトランザクションの回復
     * チェックポイントレコードを書き込む
     */
    public void recover() {
        doRecover();
        bm.flushAll(txnum);
        int lsn = CheckpointRecord.writeToLog(lm);
        lm.flush(lsn);
    }

    /**
     * 
     * @param buff
     * @param offset
     * @param newval
     * @return
     */
    public int setInt(Buffer buff, int offset, int newval) {
        BlockId blk = buff.block();
        if (isTempBlock(blk))
            return -1;
        int oldval = buff.contents().getInt(offset);
        return SetIntRecord.writeToLog(lm, txnum, blk, offset, oldval);
    }

    /**
     * 
     * @param buff
     * @param offset
     * @param newval
     * @return
     */
    public int setString(Buffer buff, int offset, String newval) {
        BlockId blk = buff.block();
        if (isTempBlock(blk))
            return -1;
        String oldval = buff.contents().getString(offset);
        return SetStringRecord.writeToLog(lm, txnum, blk, offset, oldval);
    }

    private void doRollback() {
        Iterator<byte[]> iter = lm.iterator();
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);
            if (rec.txNumber() == txnum) {
                if (rec.op() == START)
                    return;
                rec.undo(tx);
            }
        }
    }

    private void doRecover() {
        // コミットorロールバックレコードが見つかったTXリスト
        Collection<Integer> finishedTxs = new ArrayList<>();
        int earliesActiveTx = -1;                  // START検知
        Iterator<byte[]> iter = lm.iterator();     // 最新ログから遡る
        while (iter.hasNext()) {
            byte[] bytes = iter.next();
            LogRecord rec = LogRecord.createLogRecord(bytes);   // ログレコードの作成
            assert rec != null;
            System.out.println(rec);
            switch (rec.op()) {
                case CHECKPOINT:
                    return;
                case START:
                    if (earliesActiveTx == rec.txNumber())
                        return;
                    break;
                case COMMIT:
                case ROLLBACK:
                    finishedTxs.add(rec.txNumber());    // コミット・ロールバックレコードがあれば追加
                    break;
                default:
                    // commit済みリストにないTXのみundo
                    if (!finishedTxs.contains(rec.txNumber()))
                        rec.undo(tx);
                    break;
            }
        }
    }

    private boolean isTempBlock(BlockId blk) {
        return blk.fileName().startsWith("temp");
    }

}
