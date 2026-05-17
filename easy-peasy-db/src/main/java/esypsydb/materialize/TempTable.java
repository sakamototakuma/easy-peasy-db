package esypsydb.materialize;

import esypsydb.record.*;
import esypsydb.query.*;
import esypsydb.tx.Transaction;

public class TempTable {
    private static int nextTableNum = 0;
    private Transaction tx;
    private String tblname;
    private Layout layout;

    public TempTable(Transaction tx, Schema sch) {
        this.tx = tx;
        tblname = nextTableNum();
        layout = new Layout(sch);
    }

    public UpdateScan open() {
        return new TableScan(tx, tblname, layout);
    }

    public String tablename() {
        return tblname;
    }

    public Layout getLayout() {
        return layout;
    }

    /*
     * 一意な名前tempNをつける
     */
    public void close() {
        tx.closeFile(tblname + ".tbl");
    }

    public static synchronized String nextTableNum() {
        nextTableNum++;
        return "temp" + nextTableNum;
    }

 
    
}
