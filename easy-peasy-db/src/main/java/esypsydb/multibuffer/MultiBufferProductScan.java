package esypsydb.multibuffer;

import esypsydb.query.*;
import esypsydb.record.Layout;
import esypsydb.tx.Transaction;


public class MultiBufferProductScan implements Scan {
    private Scan lhsscan, rhsscan=null, prodscan;
    private Transaction tx;
    private String filename;
    private Layout layout;
    private int chunksize, nextblknum, filesize;

   public MultiBufferProductScan(Scan lhsscan, String filename, Layout layout, Transaction tx) {
        this.lhsscan = lhsscan;
        this.filename = filename;
        this.layout = layout;
        this.tx = tx;
      filesize = tx.size(filename);
        chunksize = BufferNeeds.bestFactor(tx.availableBuffs(), filesize);
        beforeFirst();
   }

   public void beforeFirst() {
        nextblknum = 0;
        useNextChunk();
   }

   public boolean next() {
      if (prodscan == null)
         return false;
      while (!prodscan.next()) 
         if (!useNextChunk())
         return false;
      return true;
   }
   
   public void close() {
      if (prodscan != null)
         prodscan.close();
      else {
         if (rhsscan != null)
            rhsscan.close();
         lhsscan.close();
      }
   }
   
   public Constant getVal(String fldname) {
      return prodscan.getVal(fldname);
   }

   public int getInt(String fldname) {
      return prodscan.getInt(fldname);
   }
   
   public String getString(String fldname) {
      return prodscan.getString(fldname);
   }
   
   public boolean hasField(String fldname) {
      return prodscan.hasField(fldname);
   }

   public boolean useNextChunk() {
    if (rhsscan != null)
        rhsscan.close();
      if (nextblknum >= filesize) {
         prodscan = null;
        return false;
      }
    int end = nextblknum + chunksize -1;
    if (end >= filesize)
        end = filesize -1;
      rhsscan = new ChunkScan(tx, filename, layout, nextblknum, end);
    lhsscan.beforeFirst();
    prodscan = new ProductScan(lhsscan, rhsscan);
    nextblknum = end + 1;
    return true;
   }
}
