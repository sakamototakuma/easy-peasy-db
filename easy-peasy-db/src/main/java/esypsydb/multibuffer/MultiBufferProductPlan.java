package esypsydb.multibuffer;

import esypsydb.plan.*;
import esypsydb.query.Scan;
import esypsydb.query.UpdateScan;
import esypsydb.record.*;
import esypsydb.tx.Transaction;
import esypsydb.materialize.*;

public class MultiBufferProductPlan implements Plan {
    private Plan lhs, rhs;
    private Transaction tx;
    private Schema schema = new Schema();

    public MultiBufferProductPlan(Plan lhs, Plan rhs, Transaction tx) {
        this.tx = tx;
        this.lhs = new MaterializePlan(tx, lhs);
        this.rhs = rhs;
        schema.addAll(lhs.schema());
        schema.addAll(rhs.schema());
    }

    public Scan open() {
        TempTable tt = copyRecordFrom(rhs);
        String filename = tt.tablename() + ".tbl";
        Layout layout = tt.getLayout();
        Scan leftscan = lhs.open();
        return new MultiBufferProductScan(leftscan, filename, layout, tx);
    }

    public String accessMethod() {
        return "multi-buffer-product";
    }

    /**
     * cost = B2 + (B1×B2/k)
     */
    public int blocksAccessed() {
        int avali = tx.availableBuffs();
        int size = new MaterializePlan(tx, rhs).blocksAccessed();
        int numchunks = (int) Math.ceil((double) size / avali);
        return rhs.blocksAccessed() + (lhs.blocksAccessed() * numchunks);
    }

    public int recordsOutput() {
        return lhs.recordsOutput() * rhs.recordsOutput();
    }

    public int distinctValues(String fldname) {
        if (lhs.schema().hasField(fldname))
            return lhs.distinctValues(fldname);
        else
            return rhs.distinctValues(fldname);
    }

    public Schema schema() {
        return schema;
    }

    private TempTable copyRecordFrom(Plan p) {
        Scan src = p.open();
        Schema sch = p.schema();
        TempTable tt = new TempTable(tx, sch);
        UpdateScan dest = (UpdateScan) tt.open();
        while (src.next()) {
            dest.insert();
            for (String fldname : sch.fields())
                dest.setVal(fldname, src.getVal(fldname));
        }
        src.close();
        dest.close();
        return tt;
    }
}
