package esypsydb.tx;

import esypsydb.buffer.BufferMgr;
import esypsydb.file.*;
import esypsydb.log.LogMgr;
import esypsydb.tx.concurrency.ConcurrencyMgr;
import esypsydb.tx.recovery.RecoveryMgr;
import esypsydb.buffer.Buffer;

public class Transaction {
    private static int nextTxNum = 0;
    private static final int END_OF_FILE = -1;
    private RecoveryMgr recoveryMgr;            // ログを書く・リカバリ
    private ConcurrencyMgr concurMgr;         // ロック(s/x)・解放
    private BufferMgr bm;
    private FileMgr fm;
    private int txnum;
    private BufferList mybuffers;               // Txがpinしてるバッファの管理

    public Transaction(FileMgr fm, LogMgr lm, BufferMgr bm) {
        this.fm = fm;
        this.bm = bm;
        txnum = nextTxNumber();
        recoveryMgr = new RecoveryMgr(this, txnum, lm, bm);
        concurMgr = new ConcurrencyMgr();
        mybuffers = new BufferList(bm);
    }

    public void commit() {
        recoveryMgr.commit();       // まずログでcommit
        concurMgr.release();        // ロック全解放
        mybuffers.unpinAll();       // 残ってるpin全て外す
    }

    public void rollback() {
        recoveryMgr.rollback();
        concurMgr.release();
        mybuffers.unpinAll();
    }

    public void recover() {
        bm.flushAll(txnum);
        recoveryMgr.recover();
    }

    public void pin(BlockId blk) {
        mybuffers.pin(blk);
    }

    public void unpin(BlockId blk) {
        mybuffers.unpin(blk);
    }

    // readメソッド:読む前に shared lock
    public int getInt(BlockId blk, int offset) {
        concurMgr.sLock(blk);
        Buffer buff = mybuffers.getBuffer(blk);
        return buff.contents().getInt(offset);
    }

    public String getString(BlockId blk, int offset) {
        concurMgr.sLock(blk);
        Buffer buff = mybuffers.getBuffer(blk);
        return buff.contents().getString(offset);
    }

    // writeメソッド：読む前に exclusive lock
    public void setInt(BlockId blk, int offset, int val, boolean okToLog) {
        concurMgr.xLock(blk);
        Buffer buff = mybuffers.getBuffer(blk);
        // ログを作成
        int lsn = -1;
        if (okToLog)
            lsn = recoveryMgr.setInt(buff, offset, val);
        Page p = buff.contents();
        p.setInt(offset, val);
        // 変更（dirty）をマーク
        buff.setModified(txnum, lsn);
    }

    public void setString(BlockId blk, int offset, String val, boolean okToLog) {
        concurMgr.xLock(blk);
        Buffer buff = mybuffers.getBuffer(blk);
        int lsn = -1;
        if (okToLog)
            lsn = recoveryMgr.setString(buff, offset, val);
        Page p = buff.contents();
        p.setString(offset, val);
        buff.setModified(txnum, lsn);
    }

    // ファイル末尾（=ブロック数）を見る
    public int size(String filename) {
        BlockId dummyblk = new BlockId(filename, END_OF_FILE);
        concurMgr.sLock(dummyblk);      // 末尾メタデータを shared lock
        return fm.length(filename);
    }

    // ファイル末尾を更新してブロック増やす
    public BlockId append(String filename) {
        BlockId dummyblk = new BlockId(filename, END_OF_FILE);
        concurMgr.xLock(dummyblk);      // 末尾メタデータを exclusive lock
        return fm.append(filename);
    }

    public void closeFile(String filename) {
        fm.closeFile(filename);
    }

    public int blockSize() {
        return fm.blockSize();
    }

    // 現在再利用可能なページ数を返す
    public int availableBuffs() {
        return bm.available();
    }

    public static synchronized int nextTxNumber() {
        nextTxNum++;
        return nextTxNum;
    }
}

