package esypsydb.materialize;

import esypsydb.plan.Plan;
import esypsydb.record.*;
import esypsydb.query.Scan;
import esypsydb.tx.Transaction;

import java.util.*;

public class GroupByPlan implements Plan {
    private Plan p;
    private Collection<String> groupfields;
    private Collection<AggregationFn> aggfns;
    private Schema sch = new Schema();

    /**
     * 
     * 
     */
    public GroupByPlan(Plan p, Collection<String> groupfields, Collection<AggregationFn> aggfns, Transaction tx) {
        List<String> grouplist = new ArrayList<String>();
        grouplist.addAll(groupfields);
        this.p = new SortPlan(p, grouplist, tx);
        this.groupfields = groupfields;
        this.aggfns = aggfns;
        for (String fldname : groupfields)
            sch.add(fldname, p.schema());
        for (AggregationFn fn : aggfns)
            sch.addIntField(fn.fieldName());
    }

    public Scan open() {
        Scan s = p.open();
        return new GroupByScan(s, groupfields, aggfns);
    }

    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    /**
     * 
     * @return group数の推定 
     * groupfieldsの各列が独立と仮定しているためかなり多めに見積もってる
     */
    public int recordsOutput() {
        int numgroups = 1;
        for (String fldname : groupfields)
            numgroups *= p.distinctValues(fldname);
        return numgroups;
    }

    /**
     * 
     * @param fldname groupfields内のfield or 集約関数の結果
     * @return - 前者ならそのdistinct Value
     *         - 後者なら必ずdistincなので出力レコード数
     */
    public int distinctValues(String fldname) {
        if (p.schema().hasField(fldname))
            return p.distinctValues(fldname);
        else
            return recordsOutput();
    }

    // GroupByPlanのスキーマはgropufields + aggregation function
    public Schema schema() {
        return sch;
    }
}
