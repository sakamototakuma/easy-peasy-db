package esypsydb.index.btree;

import static java.sql.Types.INTEGER;
import esypsydb.tx.Transaction;
import esypsydb.file.BlockId;
import esypsydb.record.*;
import esypsydb.query.Constant;

public class BTPage {
    private Transaction tx;
    private BlockId currentblk;
    private Layout layout;

    public BTPage(Transaction tx, BlockId currentblk, Layout layout) {
        this.tx = tx;
        this.currentblk = currentblk;
        this.layout = layout;
        tx.pin(currentblk);
    }

    // record位置の手前を検索
    public int findSlotBefore(Constant serchkey) {
        int slot = 0;
        while (slot < getNumRecs() &&
               getDataVal(slot).compareTo(serchkey) < 0)
            slot++;
        return slot-1;
    }

    // unnpinしてページをバッファから解放
    public void close() {
        if (currentblk != null)
            tx.unpin(currentblk);
        currentblk = null;
    }

    // index recordが満杯か判定
    public boolean isFull() {
        return slotpos(getNumRecs() + 1) >= tx.blockSize();
    }

    // ブロックが満杯時、ブロックをsplit. 親も満杯なら再帰的にsplitする
    public BlockId split(int splitpos, int flag){
        BlockId newblk = appendNew(flag);
        BTPage newpage = new BTPage(tx, newblk, layout);
        transferRecs(splitpos, newpage);
        newpage.setFlag(flag);
        newpage.close();
        return newblk;
    }

    // datavalを返す
    public Constant getDataVal(int slot) {
        return getVal(slot, "dataval");
    }

    // Flag: directory block -> level, leaf -> overflow blockのblockId
    public int getFlag() {
      return tx.getInt(currentblk, 0);
   }

    public void setFlag(int val) {
        tx.setInt(currentblk, 0, val, true);
    }

    // ファイルの最後に新しい空のページを追加
    public BlockId appendNew(int flag) {
        BlockId blk = tx.append(currentblk.fileName());
        tx.pin(blk);
        format(blk, flag);
        return blk;
    }

    // 新しいブロックをフォーマットする
    public void format(BlockId blk, int flag) {
        tx.setInt(blk, 0, flag, false);
        tx.setInt(blk, Integer.BYTES, flag, false);
        int recsize = layout.slotSize();
        for (int pos=2*Integer.BYTES; pos+recsize<=tx.blockSize();
                                      pos += recsize)
            makeDefaultRecord(blk, pos);
    }

    // 初期化の時に書き込む1行分の空データ
    public void makeDefaultRecord(BlockId blk, int pos) {
        for (String fldname : layout.schema().fields()) {
            int offset = layout.offset(fldname);
            if (layout.schema().type(fldname) == INTEGER)
                tx.setInt(blk, pos + offset, 0, false);
            else
                tx.setString(blk, pos + offset, "", false);
        }
    }


   // Methods called only by BTreeDir

   // 指定行の子ブロックの番号を読む
    public int getChildNum(int slot) {
        return getInt(slot, "block");
    }

    public void insertDir(int slot, Constant val, int blknum) {
        insert(slot);
        setVal(slot, "dataval", val);
        setInt(slot, "null", blknum);
    }

    // Methods called only by BTreeLeaf

    public RID getDataRid(int slot) {
        return new RID(getInt(slot, "block"), getInt(slot, "id"));
    }

    public void insertLeaf(int slot, Constant val, RID rid) {
        insert(slot);
        setVal(slot, "dataval", val);
        setInt(slot, "block", rid.blockNumber());
        setInt(slot, "id", rid.slot());
    }

    public void delete(int slot) {
        for (int i=slot+1; i<getNumRecs(); i++)
            copyRecord(i, i-1);
        setNumRecs(getNumRecs()-1);
        return;
    }

    // ブロックのレコード数を返す
    public int getNumRecs() {
        return tx.getInt(currentblk, Integer.BYTES);
    }

    // private methods

    // 指定行/指定列のデータを読み書き
    private int getInt(int slot, String fldname) {
        int pos = fldpos(slot, fldname);
        return tx.getInt(currentblk, pos);
    }

    private String getString(int slot, String fldname) {
        int pos = fldpos(slot, fldname);
        return tx.getString(currentblk, pos);
    }

    private Constant getVal(int slot, String fldname) {
        int type = layout.schema().type(fldname);
        if (type == INTEGER)
            return new Constant(getInt(slot, fldname));
        else
            return new Constant(getString(slot, fldname));
    }

    private void setInt(int slot, String fldname, int val) {
        int pos = fldpos(slot, fldname);
        tx.setInt(currentblk, pos, val, true);
    }
   
   private void setString(int slot, String fldname, String val) {
        int pos = fldpos(slot, fldname);
        tx.setString(currentblk, pos, val, true);
   }
   
   private void setVal(int slot, String fldname, Constant val) {
        int type = layout.schema().type(fldname);
        if (type == INTEGER)
            setInt(slot, fldname, val.asInt());
        else
            setString(slot, fldname, val.asString());
   }

   private void setNumRecs(int n) {
        tx.setInt(currentblk, Integer.BYTES, n, true);
   }

   private void insert(int slot) {
        for (int i=getNumRecs(); i>slot; i--)
            copyRecord(i-1, i);
        setNumRecs(getNumRecs()+1);
   }

   // ある行のデータを、別の行にコピぺ
   private void copyRecord(int from, int to) {
        Schema sch = layout.schema();
        for (String fldname : sch.fields())
            setVal(to, fldname, getVal(from, fldname));
   }

    private void transferRecs(int slot, BTPage dest) {
        int destslot = 0;
        while (slot < getNumRecs()) {
            dest.insert(destslot);
            Schema sch = layout.schema();
            for (String fldname : sch.fields())
                dest.setVal(destslot, fldname, getVal(slot, fldname));
            delete(slot);
            destslot++;
        }
    }

    // 定行の特定列が、ページの先頭から「何バイト目」にあるか
    private int fldpos(int slot, String fldname) {
        int offset = layout.offset(fldname);
        return slotpos(slot) + offset;
    }

    private int slotpos(int slot) {
        int slotSize = layout.slotSize();
        return Integer.BYTES + Integer.BYTES + (slot*slotSize);
    }

}
   