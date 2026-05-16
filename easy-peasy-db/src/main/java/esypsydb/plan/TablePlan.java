package esypsydb.plan;

import esypsydb.metadata.MetadataMgr;
import esypsydb.metadata.StatInfo;
import esypsydb.query.*;
import esypsydb.tx.Transaction;
import esypsydb.record.*;

public class TablePlan implements Plan {
    private Transaction tx;
    private String tblname;
    private Layout layout;
    private StatInfo si;

    public TablePlan(Transaction tx, String tblname, MetadataMgr md) {
        this.tx = tx;
        this.tblname = tblname;
        layout = md.getLayout(tblname, tx);
        si = md.getStatInfo(tblname, layout, tx);
    }

    public Scan open() {
        return (Scan) new TableScan(tx, tblname, layout);
    }

    public String accessMethod() {
        return "full-table-scan(table=" + tblname + ")";
    }

    public int blocksAccessed() {
        return si.blocksAccessed();
    }

    public int recordsOutput() {
        return si.recordsOutput();
    }

    public int distinctValues(String fldname) {
        return si.distinctValues(fldname);
    }

    public Schema schema() {
        return layout.schema();
    }
}
