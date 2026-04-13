package esypsydb.materialize;

import java.util.*;

import esypsydb.query.*;

public class GroupByScan implements Scan {
    private Scan s;
    private Collection<String> groupfields;
    private Collection<AggregationFn> aggfns;
    private GroupValue groupval;
    private boolean moregroups;

    /**
     * 
     */
    public GroupByScan(Scan s, Collection<String> groupfields, Collection<AggregationFn> aggfns) {
        this.s = s;
        this.groupfields = groupfields;
        this.aggfns = aggfns;
        beforeFirst();
    }

    // 次のgroupへ
    public void beforeFirst() {
        s.beforeFirst();
        moregroups = s.next();
    }

    /**
     * 
    */
    public boolean next() {
        if (!moregroups)
            return false;
        for (AggregationFn fn : aggfns)
            fn.processFirst(s);
        groupval = new GroupValue(s, groupfields);
        while (moregroups = s.next()) {
            GroupValue gv = new GroupValue(s, groupfields);
            if (!groupval.equals(gv))
                break;
            for (AggregationFn fn : aggfns)
                fn.proccessNext(s);
        }
        return true;
    }

    public void close() {
        s.close();
    }

    /**
     * @param fldname
     * @return fldnameがソートキーならそのまま返す / aggregation functionなら
     */
    public Constant getVal(String fldname) {
        if (groupfields.contains(fldname))
            return groupval.getVal(fldname);
        for (AggregationFn fn : aggfns)
            if (fn.fieldName().equals(fldname))
                return fn.value();
        throw new RuntimeException("field " + fldname + " not found");
    }

    public int getInt(String fldname) {
        return getVal(fldname).asInt();
    }

    public String getString(String fldname) {
        return getVal(fldname).asString();
    }

    public boolean hasField(String fldname) {
        if (groupfields.contains(fldname))
            return true;
        for (AggregationFn fn : aggfns)
            if (fn.fieldName().equals(fldname))
                return true;
        return false;
    }
}
