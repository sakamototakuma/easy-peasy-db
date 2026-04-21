package esypsydb.multibuffer;

import esypsydb.file.BlockId;
import esypsydb.query.*;
import esypsydb.record.*;
import esypsydb.tx.Transaction;

import java.util.*;
import static java.sql.Types.INTEGER;

public class ChunkScan implements Scan {
    private List<RecordPage> pages;
    private int startbnum, endbnum, current;
    private Schema sch;
    private RecordPage rp;

    public ChunkScan(TableInfo ti, int startbnum, int endbnum, Transaction tx) {
        pages = new ArrayList<RecordPage>();
        this.startbnum = startbnum;
        this.endbnum   = endbnum;
        this.sch = ti.schema();
        String filename = ti.fieldName();
        for (int i=startbnum; i<=endbnum; i++) {
            BlokId blk = new BlockId(filename, i);
            pages.add(new RecordPage(tx, blk, ti));
        }
        beforeFirst();
    }


    public void beforeFirst() {
        moveToBlock(startbnum);
    }

    public boolean next() {
        while (true) {
            if (rp.next())
                return true;
            if (current == endbnum)
                return false;
            moveToBlock(current+1);
        }
    }

    public void close() {
        for (RecordPage rp : pages)
            rp.close();
    }

    public Constant getVal(String fldname) {
      if (sch.type(fldname) == INTEGER)
         return new Constant(rp.getInt(fldname));
      else
         return new Constant(rp.getString(fldname));
   }
   
   public int getInt(String fldname) {
      return rp.getInt(fldname);
   }
   
   public String getString(String fldname) {
      return rp.getString(fldname);
   }
   
   public boolean hasField(String fldname) {
      return sch.hasField(fldname);
   }

   private void moveToBlock(int blknum) {
    current = blknum;
    rp = pages.get(current - startbnum);
    rp.moveToId(-1);
   }
}
