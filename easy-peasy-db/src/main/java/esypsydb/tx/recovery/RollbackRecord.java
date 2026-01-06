package esypsydb.tx.recovery;

import esypsydb.file.Page;
import esypsydb.log.LogMgr;
import esypsydb.tx.Transaction;

public class RollbackRecord implements LogRecord {
    private int txnum;

   /**
    * ロールバックレコードの作成
    * @param txnum 
    */
   public RollbackRecord(Page p) {
      int tpos = Integer.BYTES;
      txnum = p.getInt(tpos);
   }

   public int op() {
      return ROLLBACK;
   }

   public int txNumber() {
      return txnum;
   }

   public void undo(Transaction tx) {}

   public String toString() {
      return "<ROLLBACK " + txnum + ">";
   }

   /** 
    * A static method to write a rollback record to the log.
    * This log record contains the ROLLBACK operator,
    * followed by the transaction id.
    * @return the LSN of the last log value
    */
   public static int writeToLog(LogMgr lm, int txnum) {
      byte[] rec = new byte[2*Integer.BYTES];
      Page p = new Page(rec);
      p.setInt(0, ROLLBACK);
      p.setInt(Integer.BYTES, txnum);
      return lm.append(rec);
   }
}
