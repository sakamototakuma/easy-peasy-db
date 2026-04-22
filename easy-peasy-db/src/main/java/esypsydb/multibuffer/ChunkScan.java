package esypsydb.multibuffer;

import esypsydb.file.BlockId;
import esypsydb.query.*;
import esypsydb.record.*;
import esypsydb.tx.Transaction;

import java.util.*;
import static java.sql.Types.INTEGER;

public class ChunkScan implements Scan {
    private List<RecordPage> buffs = new ArrayList<>();
    private Transaction tx;
    private String filename;
    private Layout layout;
    private int startbnum, endbnum, currentbnum;
    private RecordPage rp;
    private int currentslot;

    public ChunkScan(Transaction tx, String filename,
                     Layout layout, int startbnum, int endbnum) {
        this.tx = tx;
        this.filename = filename;
        this.layout = layout;
        this.startbnum = startbnum;
        this.endbnum = endbnum;
        for (int i = startbnum; i <= endbnum; i++) {
            BlockId blk = new BlockId(filename, i);
            buffs.add(new RecordPage(tx, blk, layout));
        }
        moveToBlock(startbnum);
    }

    public void close() {
        for (int i = 0; i < buffs.size(); i++) {
            BlockId blk = new BlockId(filename, startbnum + i);
            tx.unpin(blk);
        }
    }

    public void beforeFirst() {
        moveToBlock(startbnum);
    }

    public boolean next() {
        currentslot = rp.nextAfter(currentslot);
        while (currentslot < 0) {
            if (currentbnum == endbnum)
                return false;
            moveToBlock(rp.block().number() + 1);
            currentslot = rp.nextAfter(currentslot);
        }
        return true;
    }

    public Constant getVal(String fldname) {
        if (layout.schema().type(fldname) == INTEGER)
            return new Constant(getInt(fldname));
        else
            return new Constant(getString(fldname));
    }

    public int getInt(String fldname) {
        return rp.getInt(currentslot, fldname);
    }

    public String getString(String fldname) {
        return rp.getString(currentslot, fldname);
    }

    public boolean hasField(String fldname) {
        return layout.schema().hasField(fldname);
    }

    private void moveToBlock(int blknum) {
        currentbnum = blknum;
        rp = buffs.get(currentbnum - startbnum);
        currentslot = -1;
    }
}
