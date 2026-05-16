package esypsydb.index.planner;

import esypsydb.index.Index;
import esypsydb.index.query.IndexJoinScan;
import esypsydb.metadata.IndexInfo;
import esypsydb.plan.Plan;
import esypsydb.query.*;
import esypsydb.record.*;

public class IndexJoinPlan implements Plan {
    private Plan p1, p2;
    private IndexInfo ii;
    private String joinfield;
    private Schema sch = new Schema();

    public IndexJoinPlan(Plan p1, Plan p2, IndexInfo ii, String joinfield) {
        this.p1 = p1;
        this.p2 = p2;
        this.ii = ii;
        this.joinfield = joinfield;
        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
    }

    public Scan open() {
        Scan s = p1.open();
        TableScan ts = (TableScan) p2.open();
        Index idx = ii.open();
        return new IndexJoinScan(s, idx, joinfield, ts);
    }

    public String accessMethod() {
        return "index-join(index=" + ii.indexName()
            + ", field=" + ii.fieldName()
            + ", outerField=" + joinfield
            + ", type=" + ii.indexType() + ")";
    }

    /*
     * 内部表を読むコスト + 内部表の各行ごとに、右表index を引くコスト
     * + 見つかった join結果件数ぶんの本体アクセス 
     */  
    public int blocksAccessed() {
        return p1.blocksAccessed() 
            + (p1.recordsOutput() * ii.blocksAccessed())
            + recordsOutput();
    }

    public int recordsOutput() {
        return p1.recordsOutput() * ii.recordsOutput();
    }

    public int distinctValues(String fldname) {
    if (p1.schema().hasField(fldname))
        return p1.distinctValues(fldname);
    else
        return p2.distinctValues(fldname);
    }

    public Schema schema() {
        return sch;
    }
}
