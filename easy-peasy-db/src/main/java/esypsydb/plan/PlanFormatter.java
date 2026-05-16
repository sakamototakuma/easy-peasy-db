package esypsydb.plan;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Plan 木を PostgreSQL 風にフォーマットする。
 *
 * <pre>
 * explain (見積もりのみ):
 *   QUERY PLAN
 *   ──────────────────────────────────────────────────────────────
 *   Project  (cost=0..1  rows=9  width=60)
 *     Output: sname, majorid
 *     ->  Filter  (cost=0..1  rows=9  width=60)
 *           Filter: (sid = 1)
 *           ->  Seq Scan on student  (cost=0..1  rows=9  width=60)
 *
 * explain analyze (実測値付き):
 *   QUERY PLAN
 *   ──────────────────────────────────────────────────────────────
 *   Project  (cost=0..1  rows=9  width=60)  (actual time=0.010..0.030 rows=1 loops=1)
 *     Output: sname, majorid
 *     ->  Filter  (cost=0..1  rows=9  width=60)  (actual time=0.005..0.020 rows=1 loops=1)
 *           Filter: (sid = 1)
 *           Rows Removed by Filter: 8
 *           ->  Seq Scan on student  (cost=0..1  rows=9  width=60)  (actual time=0.002..0.015 rows=9 loops=1)
 *   Planning Time: 0.050 ms
 *   Execution Time: 0.080 ms
 * </pre>
 */
public class PlanFormatter {

    private static final int SEPARATOR_WIDTH = 70;

    // ── 既存互換: 見積もりのみ ──

    public static String format(Plan p) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY PLAN\n");
        sb.append(repeat('─', SEPARATOR_WIDTH)).append('\n');
        formatNode(p, "", true, sb, false);
        return sb.toString();
    }

    // ── EXPLAIN ANALYZE: 実測値付き ──

    /**
     * InstrumentedPlan 木をフォーマットする（実測値付き）。
     * @param root      InstrumentedPlan.instrument() で作成されたルート
     * @param planNs    プラン作成にかかった時間 (ns)
     * @param execNs    クエリ実行にかかった時間 (ns)
     */
    public static String formatAnalyze(Plan root, long planNs, long execNs) {
        StringBuilder sb = new StringBuilder();
        sb.append("QUERY PLAN\n");
        sb.append(repeat('─', SEPARATOR_WIDTH)).append('\n');
        formatNode(root, "", true, sb, true);
        sb.append(String.format("Planning Time: %.3f ms%n", planNs / 1_000_000.0));
        sb.append(String.format("Execution Time: %.3f ms%n", execNs / 1_000_000.0));
        return sb.toString();
    }

    // ── ノード再帰フォーマット ──

    private static void formatNode(Plan p, String indent, boolean isRoot,
                                   StringBuilder sb, boolean showActuals) {
        // InstrumentedPlan の場合、ラップ元の Plan からメタ情報を取得
        Plan underlying = (p instanceof InstrumentedPlan)
                        ? ((InstrumentedPlan) p).wrappedPlan() : p;

        // ノード名
        String nodeName = underlying.nodeTypeName();

        // コスト見積もり
        String estimates = String.format("(cost=0..%d  rows=%d  width=%d)",
                underlying.blocksAccessed(),
                underlying.recordsOutput(),
                underlying.outputWidth());

        // 実測値（ANALYZE モードのみ）
        String actuals = "";
        if (showActuals && p instanceof InstrumentedPlan) {
            InstrumentedScan is = ((InstrumentedPlan) p).getInstrumentedScan();
            if (is != null) {
                actuals = String.format("  (actual time=%.3f..%.3f rows=%d loops=1)",
                        is.getFirstRowTimeMs(), is.getTotalTimeMs(), is.getRowCount());
            }
        }

        // ノード行を出力
        String prefix = isRoot ? "" : "->  ";
        sb.append(indent).append(prefix)
          .append(nodeName).append("  ")
          .append(estimates).append(actuals)
          .append('\n');

        // 追加情報行（Filter条件, Sort Key, Group Key, Output 等）
        String extraIndent = indent + (isRoot ? "  " : "    ");
        for (String line : underlying.extraInfoLines()) {
            sb.append(extraIndent).append(line).append('\n');
        }

        // Rows Removed by Filter（ANALYZE モードの SelectPlan のみ）
        if (showActuals && underlying instanceof SelectPlan && p instanceof InstrumentedPlan) {
            InstrumentedScan parentIs = ((InstrumentedPlan) p).getInstrumentedScan();
            List<InstrumentedPlan> kids = ((InstrumentedPlan) p).instrumentedChildren();
            if (parentIs != null && !kids.isEmpty()) {
                InstrumentedScan childIs = kids.get(0).getInstrumentedScan();
                if (childIs != null) {
                    int removed = childIs.getRowCount() - parentIs.getRowCount();
                    if (removed > 0) {
                        sb.append(extraIndent)
                          .append("Rows Removed by Filter: ").append(removed)
                          .append('\n');
                    }
                }
            }
        }

        // 子ノードを再帰表示
        String childIndent = indent + (isRoot ? "  " : "    ");
        for (Plan child : children(p)) {
            formatNode(child, childIndent, false, sb, showActuals);
        }
    }

    // ── 子ノード取得 ──

    private static List<Plan> children(Plan p) {
        // InstrumentedPlan の場合は明示的に保持しているリストを使う
        if (p instanceof InstrumentedPlan) {
            return new ArrayList<>(((InstrumentedPlan) p).instrumentedChildren());
        }
        // それ以外はリフレクションで Plan フィールドを探索
        List<Plan> result = new ArrayList<>();
        Class<?> c = p.getClass();
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                if (Plan.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    try {
                        Object v = f.get(p);
                        if (v != null)
                            result.add((Plan) v);
                    } catch (IllegalAccessException ignored) {
                    }
                }
            }
            c = c.getSuperclass();
        }
        return result;
    }

    // ── ユーティリティ ──

    private static String repeat(char ch, int n) {
        char[] buf = new char[n];
        for (int i = 0; i < n; i++) buf[i] = ch;
        return new String(buf);
    }
}
