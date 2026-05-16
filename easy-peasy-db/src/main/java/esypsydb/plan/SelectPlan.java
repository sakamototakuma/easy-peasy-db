package esypsydb.plan;

import java.util.List;

import esypsydb.query.*;
import esypsydb.record.Schema;

public class SelectPlan implements Plan {
    private Plan p;
    private Predicate pred;

    public SelectPlan(Plan p, Predicate pred) {
        this.p = p;
        this.pred = pred;
    }

    @Override
    public Scan open() {
        Scan s = p.open();
        return new SelectScan(s, pred);
    }

    @Override
    public String nodeTypeName() {
        return "Filter";
    }

    public String accessMethod() {
        return "filter";
    }

    @Override
    public List<String> extraInfoLines() {
        return List.of("Filter: (" + pred.toString() + ")");
    }

    @Override
    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    @Override
    public int recordsOutput() {
        // reductionFactor を使った推定
        return Math.max(1, p.recordsOutput() / pred.reductionFactor(p));
    }

    @Override
    public int distinctValues(String fldname) {
        if (pred.equatesWithConstant(fldname) != null)
            return 1;
        else {
            String fldname2 = pred.equatesWithField(fldname);
            if (fldname2 != null)
                return Math.min(p.distinctValues(fldname), p.distinctValues(fldname2));
            else
                return p.distinctValues(fldname);
        }
    }

    @Override
    public Schema schema() {
        return p.schema();
    }
}
