package esypsydb.materialize;

import esypsydb.query.Constant;
import esypsydb.query.Scan;

public class MaxFn implements AggregationFn {
    private String fldname;
    private Constant val;

    public MaxFn(String fldname) {
        this.fldname = fldname;
    }

    // current recordの値をset
    public void processFirst(Scan s) {
        val = s.getVal(fldname);
    }

    // 次の値が大きければ置き換え
    public void proccessNext(Scan s) {
        Constant newval = s.getVal(fldname);
        if (newval.compareTo(val) > 0)
            val = newval;
    }

    public String fieldName() {
      return "maxof" + fldname;
    }

    public Constant value() {
      return val;
   }
}
