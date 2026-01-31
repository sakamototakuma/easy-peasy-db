package esypsydb.tx.concurrency;

import java.util.*;
import esypsydb.file.BlockId;

public class ConcurrencyMgr {
    private static LockTable locktbl = new LockTable();
    // ロック中のBlockとロックの種類（S or X）
    private Map<BlockId, String> locks = new HashMap<BlockId, String>();

    public void sLock(BlockId blk) {
        if (locks.get(blk) == null) {
            locktbl.sLock(blk);
            locks.put(blk, "S");
        }
    }

    public void xLock(BlockId blk) {
        if (hasXlock(blk)) {
            sLock(blk);     // xLockの前にsLockしている！！
            locktbl.xLock(blk);
            locks.put(blk, "X");
        }
    }

    // Txの全てのロックを解放
    public void release() {
        for (BlockId blk : locks.keySet())
            locktbl.unlock(blk);
        locks.clear();
    }

    private boolean hasXlock(BlockId blk) {
        String locktype = locks.get(blk);
        return locktype != null && locktype.equals("X");
    }
}
