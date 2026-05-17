package esypsydb.multibuffer;

import esypsydb.materialize.TempTable;
import esypsydb.query.*;
import esypsydb.record.Layout;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

public class MultiBufferProductScan implements Scan {
   private Scan lhsscan, rhsscan = null, prodscan;
   private Transaction tx;
   private String filename;
   private Layout layout;
   private int chunksize, nextblknum, filesize;
   private Schema logicalLhsSchema, logicalRhsSchema;
   private boolean innerIsLogicalLhs;
   private TempTable tempTable; // close() 時にファイルを解放するために保持

   public MultiBufferProductScan(Scan lhsscan, String filename, Layout layout, Transaction tx) {
      this(lhsscan, filename, layout, tx, null, null, false, null);
   }

   public MultiBufferProductScan(Scan lhsscan, TempTable tt, Transaction tx,
         Schema logicalLhsSchema, Schema logicalRhsSchema,
         boolean innerIsLogicalLhs) {
      this(lhsscan, tt.tablename() + ".tbl", tt.getLayout(), tx,
            logicalLhsSchema, logicalRhsSchema, innerIsLogicalLhs, tt);
   }

   private MultiBufferProductScan(Scan lhsscan, String filename, Layout layout, Transaction tx,
         Schema logicalLhsSchema, Schema logicalRhsSchema,
         boolean innerIsLogicalLhs, TempTable tempTable) {
      this.lhsscan = lhsscan;
      this.filename = filename;
      this.layout = layout;
      this.tx = tx;
      this.logicalLhsSchema = logicalLhsSchema;
      this.logicalRhsSchema = logicalRhsSchema;
      this.innerIsLogicalLhs = innerIsLogicalLhs;
      this.tempTable = tempTable;
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
      // 一時テーブルのファイルハンドルを閉じて削除
      if (tempTable != null)
         tempTable.close();
   }
   
   public Constant getVal(String fldname) {
      return scanFor(fldname).getVal(fldname);
   }

   public int getInt(String fldname) {
      return scanFor(fldname).getInt(fldname);
   }
   
   public String getString(String fldname) {
      return scanFor(fldname).getString(fldname);
   }
   
   public boolean hasField(String fldname) {
      if (logicalLhsSchema != null && logicalRhsSchema != null)
         return logicalLhsSchema.hasField(fldname) || logicalRhsSchema.hasField(fldname);
      return prodscan.hasField(fldname);
   }

   public boolean useNextChunk() {
    if (rhsscan != null) {
        rhsscan.close();
        rhsscan = null;
    }
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

   private Scan scanFor(String fldname) {
      if (logicalLhsSchema == null)
         return prodscan;
      if (logicalLhsSchema.hasField(fldname))
         return innerIsLogicalLhs ? rhsscan : lhsscan;
      if (logicalRhsSchema.hasField(fldname))
         return innerIsLogicalLhs ? lhsscan : rhsscan;
      throw new RuntimeException("フィールドが見つかりません");
   }
}
