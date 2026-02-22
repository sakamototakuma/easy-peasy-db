package esypsydb.query;

/**
 * 条件（Predicate）に一致するレコードだけを返す読み取り専用スキャン
 */
public class SelectScan implements Scan {
    private final Predicate pred;
    protected final Scan s;

    public SelectScan(Scan s, Predicate pred) {
        this.s = s;
        this.pred = pred;
    }

    @Override
    public void beforeFirst() {
        s.beforeFirst();
    }

    @Override
    public boolean next() {
        while (s.next()) {
            if (pred.isSatisfied(s))
                return true;
        }
        return false; // 条件を満たすレコードがない
    }

    @Override
    public int getInt(String fldname) {
        return s.getInt(fldname);
    }

    @Override
    public String getString(String fldname) {
        return s.getString(fldname);
    }

    @Override
    public Constant getVal(String fldname) {
        return s.getVal(fldname);
    }

    @Override
    public boolean hasField(String fldname) {
        return s.hasField(fldname);
    }

    @Override
    public void close() {
        s.close();
    }
}
