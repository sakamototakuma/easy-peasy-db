package esypsydb.tx.recovery;

import esypsydb.file.*;
import esypsydb.log.LogMgr;
import esypsydb.tx.Transaction;

public class SetStringRecord implements LogRecord {
    private int txnum, offset;
    private String val; // val: 古い値
    private BlockId blk;

    /**
    * SetStringレコードの作成
    * @param txnum 
    */
    public SetStringRecord(Page p) {
        int tpos = Integer.BYTES;
        txnum = p.getInt(tpos);
        int fpos = tpos + Integer.BYTES;
        String filename = p.getString(fpos);
        int bpos = fpos + Page.maxLength(filename.length());
        int blknum = p.getInt(bpos);
        blk = new BlockId(filename, blknum);
        int opos = bpos + Integer.BYTES;
        offset = p.getInt(opos);
        int vpos = opos + Integer.BYTES;
        val = p.getString(vpos);
    }

    public int op() {
        return SETSTRING;
    }

    public int txNumber() {
        return txnum;
    }

    // ログを返す
    public String toString() {
        return "<SETSTRING " + txnum + " " + blk + " " + offset + " " + val + ">";
    }

    public void undo(Transaction tx) {
        System.out.println("undoing record");
        tx.pin(blk);
        tx.setString(blk, offset, val, false); // undoはログしない
        tx.unpin(blk);
    }

    // ログレコードのパッキング
    public static int writeToLog(LogMgr lm, int txnum, BlockId blk, int offset, String val) {
        int tpos = Integer.BYTES;
        int fpos = tpos + Integer.BYTES;
        String filename = blk.fileName();
        int bpos = fpos + Page.maxLength(filename.length());
        int opos = bpos + Integer.BYTES;
        int vpos = opos + Integer.BYTES;
        int reclen = vpos + Page.maxLength(val.length());  // 合計サイズ
        byte[] rec = new byte[reclen];
        Page p = new Page(rec);
        p.setInt(0, SETSTRING);
        p.setInt(tpos, txnum);
        p.setString(fpos, filename);
        p.setInt(bpos, blk.number());
        p.setInt(opos, offset);
        p.setString(vpos, val);
        return lm.append(rec);
    }
}
