package esypsydb.materialize;

import esypsydb.query.*;

public class MergeJoinScan implements Scan {
    private Scan s1;
    private SortScan s2;
    private String fldname1, fldname2;
    private Constant joinval = null;
    private boolean hasmore1;
    private boolean hasmore2;

    public MergeJoinScan(Scan s1, SortScan s2, String fldname1, String fldname2) {
        this.s1 = s1;
        this.s2 = s2;
        this.fldname1 = fldname1;
        this.fldname2 = fldname2;
        beforeFirst();
    }

    public void beforeFirst() {
        s1.beforeFirst();
        s2.beforeFirst();
        hasmore1 = s1.next();
        hasmore2 = s2.next();
        joinval = null;
    }

    public void close() {
        s1.close();
        s2.close();
    }

    /**
     * もしRHS recordが同じjoin valueを持ってるなら
     * 移動する
     * そうじゃないなら
     * もしLHS recordが同じjoin valueを持ってるなら
     * RHSのスキャン位置を戻す
     */
    public boolean next() {
        if (hasmore2 && joinval != null && s2.getVal(fldname2).equals(joinval)) {
            hasmore2 = s2.next();
            return true;
        }

        if (hasmore1 && joinval != null && s1.getVal(fldname1).equals(joinval)) {
            s2.restorePosition();
            hasmore2 = s2.next();
            return true;
        }

        while (hasmore1 && hasmore2) {
            Constant v1 = s1.getVal(fldname1);
            Constant v2 = s2.getVal(fldname2);
            int cmp = v1.compareTo(v2);
            if (cmp < 0) {
                hasmore1 = s1.next();
            } else if (cmp > 0) {
                hasmore2 = s2.next();
            } else {
                s2.savePosition();
                joinval = s2.getVal(fldname2);
                hasmore2 = s2.next();
                return true;
            }
        }
        return false;
    }

    public Constant getVal(String fldname) {
        if (s1.hasField(fldname))
            return s1.getVal(fldname);
        else
            return s2.getVal(fldname);
    }

    public int getInt(String fldname) {
        if (s1.hasField(fldname))
            return s1.getInt(fldname);
        else
            return s2.getInt(fldname);
    }

    public String getString(String fldname) {
        if (s1.hasField(fldname))
            return s1.getString(fldname);
        else
            return s2.getString(fldname);
    }

    public boolean hasField(String fldname) {
        return s1.hasField(fldname) || s2.hasField(fldname);
    }
}