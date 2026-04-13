package esypsydb.materialize;

import esypsydb.query.*;

// Key: fldname, value: valとして集約する
public class CountFn implements AggregationFn {
    private String fldname;
    private int count;

    public CountFn(String fldname) {
        this.fldname = fldname;
    }

    public void processFirst(Scan s) {
        count = 1;
    }

    public void proccessNext(Scan s) {
        count++;
    }

    public String fieldName() {
        return "countof" + fldname;
    }

    public Constant value() {
        return new Constant(count);
    }
}
