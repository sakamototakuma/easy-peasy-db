package esypsydb.record;

import static java.sql.Types.*;

import esypsydb.file.BlockId;
import esypsydb.tx.Transaction;

/**
 * スロット方式でレコードを管理するページ。
 * 各スロットの先頭 4byte は使用フラグ（EMPTY=0 / USED=1）。
 */
public class RecordPage {
    public static final int EMPTY = 0;
    public static final int USED  = 1;

    private Transaction tx;
    private BlockId blk;
    private Layout layout;

    public RecordPage(Transaction tx, BlockId blk, Layout layout) {
        this.tx = tx;
        this.blk = blk;
        this.layout = layout;
        tx.pin(blk);
    }

    public int getInt(int slot, String fldname) {
        int fldpos = offset(slot) + layout.offset(fldname);
        return tx.getInt(blk, fldpos);
    }

    public String getString(int slot, String fldname) {
        int fldpos = offset(slot) + layout.offset(fldname);
        return tx.getString(blk, fldpos);
    }

    public void setInt(int slot, String fldname, int val) {
        int fldpos = offset(slot) + layout.offset(fldname);
        tx.setInt(blk, fldpos, val, true);
    }

    public void setString(int slot, String fldname, String val) {
        int fldpos = offset(slot) + layout.offset(fldname);
        tx.setString(blk, fldpos, val, true);
    }

    public void delete(int slot) {
        setFlag(slot, EMPTY);
    }

    /** ページ内の全スロットをデフォルト値で初期化する。 */
    public void format() {
        int slot = 0;
        while (isValidSlot(slot)) {
            tx.setInt(blk, offset(slot), EMPTY, false);
            Schema sch = layout.schema();
            for (String fldname : sch.fields()) {
                int fldpos = offset(slot) + layout.offset(fldname);
                if (sch.type(fldname) == INTEGER)
                    tx.setInt(blk, fldpos, 0, false);
                else
                    tx.setString(blk, fldpos, "", false);
            }
            slot++;
        }
    }

    /** slot の後ろで USED フラグが立っている次のスロットを返す。無ければ -1。 */
    public int nextAfter(int slot) {
        return searchAfter(slot, USED);
    }

    /** slot の後ろで EMPTY なスロットを見つけ USED にして返す。無ければ -1。 */
    public int insertAfter(int slot) {
        int newslot = searchAfter(slot, EMPTY);
        if (newslot >= 0)
            setFlag(newslot, USED);
        return newslot;
    }

    public BlockId block() {
        return blk;
    }

    // ---- ヘルパ ----

    private int searchAfter(int slot, int flag) {
        slot++;
        while (isValidSlot(slot)) {
            if (tx.getInt(blk, offset(slot)) == flag)
                return slot;
            slot++;
        }
        return -1;
    }

    private boolean isValidSlot(int slot) {
        return offset(slot) + layout.slotsize() <= tx.blockSize();
    }

    private void setFlag(int slot, int flag) {
        tx.setInt(blk, offset(slot), flag, true);
    }

    private int offset(int slot) {
        return slot * layout.slotsize();
    }
}
