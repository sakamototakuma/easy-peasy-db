package esypsydb.index.hash;

import esypsydb.file.BlockId;
import esypsydb.query.Constant;
import esypsydb.record.Layout;
import esypsydb.record.RID;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

import java.sql.Types;
import java.util.Map;

/**
 * 拡張可能ハッシュテーブルの1バケットブロックを管理
 *
 * ブロックレイアウト:
 *   offset 0 : int localDepth
 *   offset 4 : int numRecords
 *   offset 8+: レコードスロット (layout.slotSize() bytes × numRecords)
 */
public class HashBucketPage {
    private static final int LOCAL_DEPTH_OFFSET = 0;
    private static final int NUM_RECORDS_OFFSET  = Integer.BYTES;
    static final int HEADER_SIZE = 2 * Integer.BYTES;

    private final Transaction tx;
    private final BlockId blk;
    private final Layout layout;

    public HashBucketPage(Transaction tx, BlockId blk, Layout layout) {
        this.tx     = tx;
        this.blk    = blk;
        this.layout = layout;
        tx.pin(blk);
    }

    public int getLocalDepth()       { return tx.getInt(blk, LOCAL_DEPTH_OFFSET); }
    public void setLocalDepth(int d) { tx.setInt(blk, LOCAL_DEPTH_OFFSET, d, true); }

    public int getNumRecords()       { return tx.getInt(blk, NUM_RECORDS_OFFSET); }
    public void clearRecords()       { tx.setInt(blk, NUM_RECORDS_OFFSET, 0, true); }

    public Constant getVal(int slot, String fld) {
        int pos = slotPos(slot) + layout.offset(fld);
        Schema sch = layout.schema();
        if (sch.type(fld) == Types.INTEGER)
            return new Constant(tx.getInt(blk, pos));
        else
            return new Constant(tx.getString(blk, pos));
    }

    /** インデックス用: (block, id) フィールドから RID を復元 */
    public RID getDataRid(int slot) {
        int blockNum = tx.getInt(blk, slotPos(slot) + layout.offset("block"));
        int slotId   = tx.getInt(blk, slotPos(slot) + layout.offset("id"));
        return new RID(blockNum, slotId);
    }

    public void insertRecord(Constant key, Map<String, Constant> row) {
        int slot = getNumRecords();
        Schema sch = layout.schema();
        for (String fld : sch.fields()) {
            int pos = slotPos(slot) + layout.offset(fld);
            Constant val = row.get(fld);
            if (val == null) val = key; // fallback (keyField のみの場合)
            if (sch.type(fld) == Types.INTEGER)
                tx.setInt(blk, pos, val.asInt(), true);
            else
                tx.setString(blk, pos, val.asString(), true);
        }
        tx.setInt(blk, NUM_RECORDS_OFFSET, slot + 1, true);
    }

    public void deleteRecord(int slot) {
        int n = getNumRecords();
        for (int i = slot; i < n - 1; i++)
            copyRecord(i + 1, i);
        tx.setInt(blk, NUM_RECORDS_OFFSET, n - 1, true);
    }

    public boolean isFull() {
        int capacity = (tx.blockSize() - HEADER_SIZE) / layout.slotSize();
        return getNumRecords() >= capacity;
    }

    public void close() { tx.unpin(blk); }

    private int slotPos(int slot) {
        return HEADER_SIZE + slot * layout.slotSize();
    }

    private void copyRecord(int from, int to) {
        Schema sch = layout.schema();
        for (String fld : sch.fields()) {
            int fromPos = slotPos(from) + layout.offset(fld);
            int toPos   = slotPos(to)   + layout.offset(fld);
            if (sch.type(fld) == Types.INTEGER)
                tx.setInt(blk, toPos, tx.getInt(blk, fromPos), true);
            else
                tx.setString(blk, toPos, tx.getString(blk, fromPos), true);
        }
    }
}
