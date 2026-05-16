package esypsydb.index.planner;

import esypsydb.index.Index;
import esypsydb.index.query.IndexSelectScan;
import esypsydb.metadata.IndexInfo;
import esypsydb.plan.Plan;
import esypsydb.query.Constant;
import esypsydb.query.Scan;
import esypsydb.record.Schema;
import esypsydb.record.TableScan;

public class IndexSelectPlan implements Plan {
    private Plan p;
    private IndexInfo ii;
    private Constant val;

    public IndexSelectPlan(Plan p, IndexInfo ii, Constant val) {
      this.p = p;
      this.ii = ii;
      this.val = val;
   }
   
   /** 
    * Creates a new indexselect scan for this query
    * @see simpledb.plan.Plan#open()
    */
   public Scan open() {
      // throws an exception if p is not a tableplan.
      TableScan ts = (TableScan) p.open();
      Index idx = ii.open();
      return new IndexSelectScan(ts, idx, val);
   }

   public String accessMethod() {
      return "index-select(index=" + ii.indexName()
            + ", field=" + ii.fieldName()
            + ", type=" + ii.indexType() + ")";
   }
   
   public int blocksAccessed() {
      return ii.blocksAccessed() + recordsOutput();
   }
   
   public int recordsOutput() {
      return ii.recordsOutput();
   }
   
   public int distinctValues(String fldname) {
      return ii.distinctValues(fldname);
   }
   
   public Schema schema() {
      return p.schema(); 
   }
}
