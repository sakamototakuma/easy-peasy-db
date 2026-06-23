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
        TablePlanner[] tps = new TablePlanner[n];
        Plan[] access = new Plan[n];
        for (int i = 0; i < n; i++) {
            tps[i] = new TablePlanner(tables.get(i), pred, tx, mdm, useIndex);
            access[i] = tps[i].makeSelectPlan();
        }

        // DP
        // lowest[mask] = mask が示すテーブル集合に対する最良プラン
        // mask はビットマスク（bit i = tables[i]）
        Map<Integer, Plan> lowest = new HashMap<>();

        // 単一テーブル
        for (int i = 0; i < n; i++)
            lowest.put(1 << i, access[i]);

        // size=2->nで部分集合を拡大
        for (int size = 2; size <= n; size++) {
            for (int mask = 1; mask < (1 << n); mask++) {
                if (Integer.bitCount(mask) != size)
                    continue;
                Plan best = null;

                // maskから各テーブルtを1つ取り除き、
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

        // Projection
        int allMask = (1 << n) - 1;
        return new ProjectPlan(lowest.get(allMask), data.fields());
    }

    // 最適なjoinのコストベース比較
    private Plan bestJoin(Plan left, TablePlanner tp, Predicate pred, Transaction tx) {
        Plan best;

        // Product Join
        best = tp.makeProductPlan(left);
        Predicate joinPred = pred.joinSubPred(left.schema(), tp.makeSelectPlan().schema());
        if (joinPred != null)
            best = new SelectPlan(best, joinPred);

        // Index Join
        Plan indexJoin = tp.makeJoiPlan(left);
        if (indexJoin != null && indexJoin.blocksAccessed() < best.blocksAccessed())
            best = indexJoin;

        // Merge Join（等結合述語がある場合）
        Plan mergeJoin = tryMergeJoin(left, tp.makeSelectPlan(), pred, tx);
        if (mergeJoin != null && mergeJoin.blocksAccessed() < best.blocksAccessed())
            best = mergeJoin;

        return best;
    }

    private Plan tryMergeJoin(Plan left, Plan right, Predicate pred, Transaction tx) {
        Schema leftSch = left.schema();
        Schema rightSch = right.schema();

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
