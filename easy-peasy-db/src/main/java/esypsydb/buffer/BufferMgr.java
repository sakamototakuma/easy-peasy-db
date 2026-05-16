package esypsydb.buffer;

import esypsydb.file.BlockId;
import esypsydb.file.FileMgr;
import esypsydb.log.LogMgr;

public class BufferMgr {
    private Buffer[] bufferpool;
    private int numAvailable;
    private static final long MAX_TIME = 10000;  // 10 seconds

    public BufferMgr(FileMgr fm, LogMgr lm, int numbuffs) {
        bufferpool = new Buffer[numbuffs];
        numAvailable = numbuffs;
        // 中身が空のバッファを割り当て
        for (int i=0; i<numbuffs; i++) {
            bufferpool[i] = new Buffer(fm, lm);
        }
    }

    // 現在再利用可能なページ数を返す
    public synchronized int available() {
        return numAvailable;
    }

    // 指定 tx が変更した dirty バッファを書き出し（WAL 用）
    public synchronized void flushAll(int txnum) {
        for (Buffer buff : bufferpool)
            if (buff.modifyingTx() == txnum)
                buff.flush();
    }

    // プール内の全 dirty バッファを書き出し（チェックポイント用）
    public synchronized void flushAllDirty() {
        for (Buffer buff : bufferpool)
            buff.flush();
    }

    // unpin：バッファの返却
    public synchronized void unpin(Buffer buff) {
        buff.unpin();
        if (!buff.isPinned()) {
            numAvailable++;
            notifyAll();        // 他のスレッドに通知
        }
    }

    // pin：ピン留めのためのバッファ確保
    public synchronized Buffer pin(BlockId blk) {
        try {
            long timestamp = System.currentTimeMillis();            // 開始時刻
            Buffer buff = tryToPin(blk);                            // 確保を試みる
            // 確保失敗かつ制限時間内ならループして待ち続ける
            while (buff == null && !waitingTooLong(timestamp)) {
                wait(MAX_TIME);
                buff = tryToPin(blk);
            }
            // 制限時間に達したら失敗
            if (buff == null)
                throw new BufferAbortException();
            return buff;
        }
        catch(InterruptedException e) {
            throw new BufferAbortException();
        }
    }

    // タイムアウト判定
    private boolean waitingTooLong(long starttime) {
        return System.currentTimeMillis() - starttime >= MAX_TIME;
    }

    // 指定されたブロックを「既存からの検索」or「新規割り当て」で確保
    private Buffer tryToPin(BlockId blk) {
        Buffer buff = findExistingBuffer(blk);
        // バッファ内にない場合
        if (buff == null) {
            // ピン留めされてないバッファを選択
            buff = chooseUnpinnedBuffer();
            // バッファ内にない場合、確保失敗
            if (buff == null)
                return null;
            // ブロックの割り当て
            buff.assignToBlock(blk);
        }
        // 新しく確保したバッファであれば利用可能なバッファ数-1
        if (!buff.isPinned())
            numAvailable--;
        buff.pin();
        return buff;
    }

    // プール内を全走査して、同じBlockIdがバッファ内にあるか確認
    private Buffer findExistingBuffer(BlockId blk) {
        for (Buffer buff : bufferpool) {
            BlockId b = buff.block();
            if (b != null && b.equals(blk))
                return buff;
        }
        return null;
    }

    // プール内のピンが立ってないバッファを見つけた順に返す
    private Buffer chooseUnpinnedBuffer() {
        for (Buffer buff : bufferpool)
            if (!buff.isPinned())
                return buff;
        return null;
    }
}
