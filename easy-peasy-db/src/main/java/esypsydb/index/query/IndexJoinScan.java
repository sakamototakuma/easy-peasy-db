package esypsydb.index.query;

import esypsydb.index.Index;
import esypsydb.query.*;
import esypsydb.record.TableScan;


public class IndexJoinScan implements Scan {
    private Scan lhs;
    private Index idx;
    private String joinfield;
    private TableScan rhs;

    public IndexJoinScan(Scan lhs, Index idx, String joinfield, TableScan rhs) {
        this.lhs = lhs;
        this.idx  = idx;
        this.joinfield = joinfield;
        this.rhs = rhs;
        beforeFirst();
    }

    public void beforeFirst() {
        lhs.beforeFirst();
        lhs.next();
        resetIndex();
    }

    // 内部表の各行に対し、外部表のインデックスで一致行を探す
    public boolean next() {
        while (true) {
            if (idx.next()) {
                rhs.moveToRid(idx.getDataRid());
                return true;
            }
            if (!lhs.next())
                return false;
            resetIndex();
        }
    }

    // 外部表にあるか確認 無ければ内部表
    public Constant getVal(String fldname) {
      if (rhs.hasField(fldname))
         return rhs.getVal(fldname);
      else
         return lhs.getVal(fldname);
   }

    public int getInt(String fldname) {
        if (rhs.hasField(fldname))
            return rhs.getInt(fldname);
        else
            return lhs.getInt(fldname);
    }

    public String getString(String fldname) {
      if (rhs.hasField(fldname))
         return rhs.getString(fldname);
      else
         return lhs.getString(fldname);
   }

   public boolean hasField(String fldname) {
    return rhs.hasField(fldname) || lhs.hasField(fldname);
   }

   public void close() {
    lhs.close();
    idx.close();
    rhs.close();
   }

   private void resetIndex() {
        Constant searchKey = lhs.getVal(joinfield);
        idx.beforeFirst(searchKey);
   }
}
