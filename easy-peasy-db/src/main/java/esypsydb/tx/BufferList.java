package esypsydb.tx;
import esypsydb.buffer.*;
import esypsydb.file.BlockId;
import java.util.*;

// 
public class BufferList {
    private Map<BlockId, Buffer> buffers = new HashMap<>();     // ブロックに対応するバッファ
    private List<BlockId> pins = new ArrayList<>();             // そのブロックはTxで何回pinされたか
    private BufferMgr bm;

    public BufferList(BufferMgr bm) {
        this.bm = bm;
    }

    // 指定したブロックを取得
    Buffer getBuffer(BlockId blk) {
        return buffers.get(blk);
    }

    void pin(BlockId blk) {
        Buffer buff = bm.pin(blk);
        buffers.put(blk, buff);
        pins.add(blk);
    }

    void unpin(BlockId blk) {
        Buffer buff = buffers.get(blk);
        bm.unpin(buff);
        pins.remove(blk);
        if (!pins.contains(blk))
            buffers.remove(blk);
    }

    // commit/rollback 時
    // 	Txが変更したバッファをflush（Txの更新を確定/回復可能にする）
	//  まだpinしてるバッファを全部unpin
    void unpinAll() {
        for (BlockId blk : pins) {
            Buffer buff = buffers.get(blk);
            bm.unpin(buff);
        }
        buffers.clear();
        pins.clear();
    }
}
