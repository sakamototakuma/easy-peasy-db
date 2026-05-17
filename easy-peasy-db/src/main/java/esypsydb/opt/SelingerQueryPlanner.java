package esypsydb.opt;

import java.util.*;

import esypsydb.materialize.MergeJoinPlan;
import esypsydb.metadata.MetadataMgr;
import esypsydb.parse.QueryData;
import esypsydb.plan.*;
import esypsydb.query.Predicate;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

/**
 * Selinger-style query optimizer（動的計画法ベース）
 *
 * HeuristicQueryPlanner が貪欲法で join order を選ぶのに対し、
 * このオプティマイザはすべての left-deep join order を列挙し、
 * blocksAccessed()（I/O コスト）が最小のプランを動的計画法で求める。
 *
 * <h3>アルゴリズム概要</h3>
 * 
 * lowest[{t}] = テーブル t の最良アクセスプラン（インデックス選択 + 選択述語）
 *
 * for size = 2 to N:
 * for 各サブセット S (|S| = size):
 * for 各テーブル t ∈ S:
 * candidate = bestJoin(lowest[S＼{t}], t)
 * lowest[S] = min(lowest[S], candidate) ← blocksAccessed で比較
 *
 * result = ProjectPlan(lowest[全テーブル], fields)
 *
 * <h3>HeuristicQueryPlanner との違い</h3>
 * <ul>
 * <li>最適化指標: recordsOutput ではなく <b>blocksAccessed</b>（I/O コスト）</li>
 * <li>探索方式: 貪欲法ではなく <b>動的計画法</b>（部分解を捨てない）</li>
 * <li>結合方式: Product だけでなく <b>Index Join / Merge Join</b> も候補に含める</li>
 * </ul>
 */
public class SelingerQueryPlanner implements QueryPlanner {
    private final MetadataMgr mdm;
    private final boolean useIndex;

    public SelingerQueryPlanner(MetadataMgr mdm) {
        this(mdm, true);
    }

    public SelingerQueryPlanner(MetadataMgr mdm, boolean useIndex) {
        this.mdm = mdm;
        this.useIndex = useIndex;
    }

    @Override
    public Plan createPlan(QueryData data, Transaction tx) {
        List<String> tables = new ArrayList<>(data.tables());
        int n = tables.size();
        Predicate pred = data.pred();

        // ── Step 1: 各テーブルの最良アクセスプラン ──────────────
        // TablePlanner を使ってインデックス選択 + 選択述語の pushdown を行う
        TablePlanner[] tps = new TablePlanner[n];
        Plan[] access = new Plan[n]; // 選択述語適用済みの単一テーブルプラン
        for (int i = 0; i < n; i++) {
            tps[i] = new TablePlanner(tables.get(i), pred, tx, mdm, useIndex);
            access[i] = tps[i].makeSelectPlan();
        }

        // ── Step 2: 動的計画法 ─────────────────────────────────
        // lowest[mask] = mask が示すテーブル集合に対する最良プラン
        // mask はビットマスク（bit i = tables[i]）
        Map<Integer, Plan> lowest = new HashMap<>();

        // Base case: 単一テーブル
        for (int i = 0; i < n; i++)
            lowest.put(1 << i, access[i]);

        // size=2 から n まで、部分集合を拡大
        for (int size = 2; size <= n; size++) {
            for (int mask = 1; mask < (1 << n); mask++) {
                if (Integer.bitCount(mask) != size)
                    continue;

                Plan best = null;

                // mask から各テーブル t を 1 つ取り除き、
                // lowest[rest] と t の結合を試す（left-deep）
                for (int i = 0; i < n; i++) {
                    int bit = 1 << i;
                    if ((mask & bit) == 0)
                        continue;

                    int rest = mask ^ bit;
                    Plan left = lowest.get(rest);
                    if (left == null)
                        continue;

                    Plan candidate = bestJoin(left, tps[i], pred, tx);
                    if (best == null || candidate.blocksAccessed() < best.blocksAccessed())
                        best = candidate;
                }

                lowest.put(mask, best);
            }
        }

        // ── Step 3: Projection ─────────────────────────────────
        int allMask = (1 << n) - 1;
        return new ProjectPlan(lowest.get(allMask), data.fields());
    }

    // ────────────────────────────────────────────────────────────
    // left プランと右テーブルを結合する最良の方法を選ぶ。
    // 候補: Index Join / Merge Join / Product Join
    // 比較指標: blocksAccessed()
    // ────────────────────────────────────────────────────────────

    private Plan bestJoin(Plan left, TablePlanner tp, Predicate pred, Transaction tx) {
        Plan best;

        // ベースライン: Product Join（常に可能）
        best = tp.makeProductPlan(left);
        Predicate joinPred = pred.joinSubPred(left.schema(), tp.makeSelectPlan().schema());
        if (joinPred != null)
            best = new SelectPlan(best, joinPred);

        // 候補 1: Index Join（TablePlanner に委譲）
        Plan indexJoin = tp.makeJoiPlan(left);
        if (indexJoin != null && indexJoin.blocksAccessed() < best.blocksAccessed())
            best = indexJoin;

        // 候補 2: Merge Join（等結合述語がある場合）
        Plan mergeJoin = tryMergeJoin(left, tp.makeSelectPlan(), pred, tx);
        if (mergeJoin != null && mergeJoin.blocksAccessed() < best.blocksAccessed())
            best = mergeJoin;

        return best;
    }

    /**
     * 左プランと右プランの間に等結合述語があれば MergeJoinPlan を試す。
     * join述語がなければ null を返す。
     */
    private Plan tryMergeJoin(Plan left, Plan right, Predicate pred, Transaction tx) {
        Schema leftSch = left.schema();
        Schema rightSch = right.schema();

        // 右テーブルの各フィールドに対して、左側と等結合になる列を探す
        for (String rightFld : rightSch.fields()) {
            String leftFld = pred.equatesWithField(rightFld);
            if (leftFld != null && leftSch.hasField(leftFld)) {
                Plan p = new MergeJoinPlan(left, right, leftFld, rightFld, tx);
                // merge 条件以外の結合述語をフィルタとして追加
                Predicate remaining = pred.joinSubPred(leftSch, rightSch);
                if (remaining != null)
                    p = new SelectPlan(p, remaining);
                return p;
            }
        }
        return null;
    }
}
