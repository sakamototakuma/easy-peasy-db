package esypsydb.plan;

import esypsydb.query.Constant;
import esypsydb.query.Scan;

/**
 * Scan をラップし、行数とタイミングを計測する。
 * PostgreSQL の EXPLAIN ANALYZE でノードごとの actual time / actual rows を出すために使用。
 */
public class InstrumentedScan implements Scan {
    private final Scan wrapped;
    private long accumulatedNs = 0;
    private long firstRowNs    = -1;
    private long totalNs       = -1;
    private int  rowCount      = 0;

    public InstrumentedScan(Scan wrapped) {
        this.wrapped = wrapped;
    }

    @Override
    public void beforeFirst() {
        wrapped.beforeFirst();
        // beforeFirst が呼ばれたらカウンタをリセット
        accumulatedNs = 0;
        firstRowNs    = -1;
        totalNs       = -1;
        rowCount      = 0;
    }

    @Override
    public boolean next() {
        long t0 = System.nanoTime();
        boolean hasNext = wrapped.next();
        long elapsed = System.nanoTime() - t0;
        accumulatedNs += elapsed;
        if (hasNext) {
            rowCount++;
            if (firstRowNs == -1)
                firstRowNs = accumulatedNs;
        } else {
            totalNs = accumulatedNs;
            if (firstRowNs == -1)
                firstRowNs = accumulatedNs; // 0行の場合
        }
        return hasNext;
    }

    @Override
    public int getInt(String fldname) {
        return wrapped.getInt(fldname);
    }

    @Override
    public String getString(String fldname) {
        return wrapped.getString(fldname);
    }

    @Override
    public Constant getVal(String fldname) {
        return wrapped.getVal(fldname);
    }

    @Override
    public boolean hasField(String fldname) {
        return wrapped.hasField(fldname);
    }

    @Override
    public void close() {
        if (totalNs == -1)
            totalNs = accumulatedNs;
        wrapped.close();
    }

    // ─── 計測結果アクセサ ───

    /** 最初の行が返されるまでの累積時間（ms） */
    public double getFirstRowTimeMs() {
        return (firstRowNs == -1 ? 0 : firstRowNs) / 1_000_000.0;
    }

    /** 全行走査にかかった累積時間（ms） */
    public double getTotalTimeMs() {
        return (totalNs == -1 ? accumulatedNs : totalNs) / 1_000_000.0;
    }

    /** 返された行数 */
    public int getRowCount() {
        return rowCount;
    }
}
