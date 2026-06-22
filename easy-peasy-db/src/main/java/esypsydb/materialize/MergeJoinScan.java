package esypsydb.materialize;

import esypsydb.query.*;

public class MergeJoinScan implements Scan {
    private Scan s1;
    private SortScan s2;
    private String fldname1, fldname2;
    private Constant joinval = null;

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
        joinval = null;
    }

    public void close() {
        s1.close();
        s2.close();
    }

    /**
     * Merge Join の前進
     * 1. RHS(s2)を1つ進め まだ joinval と同じ値なら 同一グループの続きとして emit
     * 2. そうでなければ LHS(s1)を1つ進め 新しい s1 が joinval と同じなら
     *    RHS を保存位置へ巻き戻して同一グループを頭から再走査して emit
     * 3. どちらでもなければ 値が一致するまで小さい側を進める
     *    一致したら RHS 位置を保存 & joinval を更新して emit
     */
    public boolean next() {
        boolean hasmore2 = s2.next();
        if (hasmore2 && joinval != null && s2.getVal(fldname2).equals(joinval))
            return true;

        boolean hasmore1 = s1.next();
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