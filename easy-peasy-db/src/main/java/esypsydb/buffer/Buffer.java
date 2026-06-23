package esypsydb.buffer;

import esypsydb.file.*;
import esypsydb.log.LogMgr;

public class Buffer {
    private FileMgr fm;
    private LogMgr lm;
    private Page contents;       // ディスクから読み込んだ内容を保持するメモリ領域
    private BlockId blk = null;
    private int pins = 0;        // ピンの数（トランザクション数）
    private int txnum = -1;      // トランザクション番号　（0以上:=Tx番号(dirty), -1:＝Clean）
    private int lsn = -1;        // バッファに対するLSN

    public Buffer(FileMgr fm, LogMgr lm) {
        this.fm = fm;
        this.lm = lm;
        contents = new Page(fm.blockSize());
    }

    // 生のページの中身を返す
    public Page contents() {
        return contents;
    }

    // 載っているブロックを返す
    public BlockId block() {
        return blk;
    }

    // ページを変更したtxnumと、変更のログレコードを返す必要がある
    public void setModified(int txnum, int lsn) {
        this.txnum = txnum;
        if (lsn >= 0) this.lsn = lsn;
    }

    // pinのフラグ
    public boolean isPinned() {
        return pins > 0;
    }

    // バッファを変更したtxnumを返す
    public int modifyingTx() {
        return txnum;
    }

    // ページの再利用のために今の内容を書き込む
    // 別のブロックbのデータを読み込む
    void assignToBlock(BlockId b) {
        flush();
        blk = b;
        fm.read(blk, contents);  // 新しいブロックを読み込む
        txnum = -1;              // 変更のリセット
    }

    // Relacement: 変更があれば
    // 1. まずログに書き込み
    // 2. データ本体を書き込み
    void flush() {
        if (txnum >= 0) {
            lm.flush(lsn);      // lsn最新までを書き込み
            fm.write(blk, contents);
            txnum = -1;
        }
    }

    void pin() {
        pins++;
    }

    void unpin() {
        pins--;
    }
}
