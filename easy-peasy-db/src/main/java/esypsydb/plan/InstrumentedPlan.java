package esypsydb.plan;

import esypsydb.query.Scan;
import esypsydb.record.Schema;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Plan をラップし、open() 時に InstrumentedScan で計測する。
 * リフレクションで元の Plan の子 Plan フィールドを InstrumentedPlan に差し替え、
 * 木全体でノードごとの実測値を取得できるようにする。
 */
public class InstrumentedPlan implements Plan {
    private final Plan wrapped;
    private InstrumentedScan instrumentedScan;
    private final List<InstrumentedPlan> instrumentedChildren = new ArrayList<>();

    private InstrumentedPlan(Plan wrapped) {
        this.wrapped = wrapped;
    }

    /**
     * Plan 木全体を再帰的に InstrumentedPlan でラップする。
     * 元の Plan の子フィールドをリフレクションで InstrumentedPlan に差し替える。
     */
    public static InstrumentedPlan instrument(Plan root) {
        InstrumentedPlan wrapper = new InstrumentedPlan(root);

        Class<?> c = root.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Plan.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Plan child = (Plan) f.get(root);
                        if (child != null) {
                            InstrumentedPlan instrumentedChild = instrument(child);
                            f.set(root, instrumentedChild);
                            wrapper.instrumentedChildren.add(instrumentedChild);
                        }
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return wrapper;
    }

    // ─── Plan インターフェース委譲 ───

    @Override
    public Scan open() {
        Scan raw = wrapped.open();
        instrumentedScan = new InstrumentedScan(raw);
        return instrumentedScan;
    }

    @Override
    public int blocksAccessed() {
        return wrapped.blocksAccessed();
    }

    @Override
    public int recordsOutput() {
        return wrapped.recordsOutput();
    }

    @Override
    public int distinctValues(String fldname) {
        return wrapped.distinctValues(fldname);
    }

    @Override
    public Schema schema() {
        return wrapped.schema();
    }

    @Override
    public String scanName() {
        return wrapped.scanName();
    }

    @Override
    public String accessMethod() {
        return wrapped.accessMethod();
    }

    @Override
    public String nodeTypeName() {
        return wrapped.nodeTypeName();
    }

    @Override
    public int outputWidth() {
        return wrapped.outputWidth();
    }

    @Override
    public List<String> extraInfoLines() {
        return wrapped.extraInfoLines();
    }

    /**
     * Scan を open → 全行走査 → close し、実行時間 (ns) を返す。
     * 走査中に各ノードの InstrumentedScan へ実測値が記録される。
     */
    public long execute() {
        long start = System.nanoTime();
        Scan s = open();
        try {
            while (s.next()) {
                /* InstrumentedScan が計測 */ }
        } finally {
            s.close();
        }
        return System.nanoTime() - start;
    }

    // ─── 計測結果アクセサ ───

    /** ラップ元の Plan を返す */
    public Plan wrappedPlan() {
        return wrapped;
    }

    /** open() 後に設定される InstrumentedScan */
    public InstrumentedScan getInstrumentedScan() {
        return instrumentedScan;
    }

    /** instrument() で差し替えた子 InstrumentedPlan のリスト */
    public List<InstrumentedPlan> instrumentedChildren() {
        return Collections.unmodifiableList(instrumentedChildren);
    }
}
