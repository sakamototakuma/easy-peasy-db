package esypsydb.materialize;

import esypsydb.query.*;

public interface AggregationFn {
    void processFirst(Scan s);
    void proccessNext(Scan s);
    String fieldName();
    Constant value();
}
