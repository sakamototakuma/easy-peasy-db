package esypsydb.index.btree;

import esypsydb.file.BlockId;
import esypsydb.record.Layout;
import esypsydb.tx.Transaction;
import esypsydb.query.*;

public class BTreeDir {
    private Transaction tx;
    private Layout layout;
    private BTPage contents;
    private String filename;

    BTreeDir(Transaction tx, BlockId blk, Layout layout) {
        this.tx = tx;
        this.layout = layout;
        contents = new BTPage(tx, blk, layout);
        filename = blk.fileName();
    }

    public void close() {
        contents.close();
   }

   // searchkeyに含まれるエントリのブロック数を返す
   public int search(Constant searchkey) {
        BlockId childblk = findChildBlock(searchkey);
        while (contents.getFlag() > 0) {
            contents.close();
            contents = new BTPage(tx, childblk, layout);
            childblk = findChildBlock(searchkey);
        }
        return childblk.number();
   }

   public void makeNewRoot(DirEntry e) {
        Constant firstval = contents.getDataVal(0);
        int level = contents.getFlag();
        BlockId newblk = contents.split(0, level); // 例: ブロックをsplit
        DirEntry oldroot = new DirEntry(firstval, newblk.number());
        insertEntry(oldroot);
        insertEntry(e);
        contents.setFlag(level+1);
   }

   public DirEntry insert(DirEntry e) {
    if (contents.getFlag() == 0)
        return insertEntry(e);
    BlockId childblk = findChildBlock(e.dataVal());
    BTreeDir child = new BTreeDir(tx, childblk, layout);
    DirEntry myentry = child.insert(e);
    child.close();
    return (myentry != null) ? insertEntry(myentry) : null;
   }

   private DirEntry insertEntry(DirEntry e) {
        int newslot = 1 + contents.findSlotBefore(e.dataVal());
        contents.insertDir(newslot, e.dataVal(), e.blockNumber());
        if (!contents.isFull())
        return null;
        // else page is full, so split it
        int level = contents.getFlag();
        int splitpos = contents.getNumRecs() / 2;
        Constant splitval = contents.getDataVal(splitpos);
        BlockId newblk = contents.split(splitpos, level);
        return new DirEntry(splitval, newblk.number());
    }

   private BlockId findChildBlock(Constant searchkey) {
        int slot = contents.findSlotBefore(searchkey);
        if (contents.getDataVal(slot+1).equals(searchkey))
            slot++;
        int blknum = contents.getChildNum(slot);
        return new BlockId(filename, blknum);
   }
}
