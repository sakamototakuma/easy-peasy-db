package esypsydb.tx.concurrency;
import esypsydb.file.*;
import java.util.Map;
import java.util.HashMap;

public class LockTable {
    private static final long MAX_TIME = 10000; // 10 seconds

    private Map<BlockId, Integer> locks = new HashMap<BlockId, Integer>();

    public synchronized void sLock(BlockId blk) {
        try {
            long timestamp = System.currentTimeMillis();
            while (hasXlock(blk) && !waitingTooLong(timestamp)) {
                wait(MAX_TIME);
            }
            // 抜けた後もう一回見て、まだロック中ならabort
            if (hasXlock(blk))
                throw new LockAbortException();
            int val = getLockVal(blk);
            locks.put(blk, val+1);  // 共有ロック1本追加
        }
        catch(InterruptedException e) {
            throw new LockAbortException();
        }
    }

    public synchronized void xLock(BlockId blk) {
        try {
            long timestamp = System.currentTimeMillis();
            // ロックの解放待ち & タイムアウトまで
            while (hasOtherSLocks(blk) && !waitingTooLong(timestamp)) {
                wait(MAX_TIME);
            }
            if (hasOtherSLocks(blk))
                throw new LockAbortException();
            locks.put(blk, -1); // 排他化
        }
        catch(InterruptedException e) {
            throw new LockAbortException();
        }
    }

    public synchronized void unlock(BlockId blk) {
        int val = getLockVal(blk);
        // 共有者がいるので-1
        if (val > 1)
            locks.put(blk, val-1);
        // val =< 1 -> 最後の共有者or排他ロックなのでMapから消す
        else {
            locks.remove(blk);
            notifyAll();
        }
    }

    private boolean hasXlock(BlockId blk) {
        return getLockVal(blk) < 0;
    }

    private boolean hasOtherSLocks(BlockId blk) {
        return getLockVal(blk) > 1;
    }

    // タイムアウト判定
    private boolean waitingTooLong(long starttime) {
       return System.currentTimeMillis() - starttime > MAX_TIME; 
    }

    // ロックの本数
    private int getLockVal(BlockId blk) {
        Integer ival = locks.get(blk);
        return (ival == null) ? 0 : ival.intValue();
    }
}
