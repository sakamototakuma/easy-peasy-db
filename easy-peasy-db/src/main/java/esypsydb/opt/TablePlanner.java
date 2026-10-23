package esypsydb.opt;

import esypsydb.query.*;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;
import esypsydb.index.planner.*;
import esypsydb.materialize.HashJoinPlan;
import esypsydb.metadata.IndexInfo;
import esypsydb.metadata.MetadataMgr;
import esypsydb.multibuffer.MultiBufferProductPlan;
import esypsydb.plan.*;
import java.util.Map;
import java.util.TreeSet;



public class TablePlanner {
    // seq scan と index scan の比較に使うブロックの重み。PostgreSQL の既定値
    // (seq_page_cost=1.0, random_page_cost=4.0) の流用で、このエンジンでの実測はない。意味があるのは比だけ
    private static final double W_SEQ = 1.0;
    private static final double W_RANDOM = 4.0;

    private TablePlan myplan;
    private Predicate mypred;
    private Schema myschema;
    private Map<String, IndexInfo> indexes;
    private Transaction tx;
    private boolean useIndex;

    public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm) {
        this(tblname, mypred, tx, mdm, true);
    }

    public TablePlanner(String tblname, Predicate mypred, Transaction tx, MetadataMgr mdm, boolean useIndex) {
        this.mypred = mypred;
        this.tx = tx;
        this.useIndex = useIndex;
        myplan = new TablePlan(tx, tblname, mdm);
        myschema = myplan.schema();
        indexes = mdm.getIndexInfo(tblname, tx);
    }

    // 選択述語適用前の生レコード数
    public int rawOutput() {
        return myplan.recordsOutput();
    }

    // Index Selectできるか試す
    public Plan makeSelectPlan() {
        Plan p = makeIndexSelect();
        if (p == null)
            p = myplan;
        return addSelectPred(p);
    }

    /**
     * Joinプランを特定のplanとテーブルから作成する
     * index joinを使う if possible
     * joinpredがないならnullを返す
     * 
     * @param current
     * @return join plan
     */
    public Plan makeJoiPlan(Plan current) {
        Schema currsch = current.schema();
        Predicate joinpred = mypred.joinSubPred(myschema, currsch);

        if (joinpred == null)
            return null;
        Plan hashPlan  = makeHashJoin(current, currsch);
        Plan indexPlan = makeIndexJoin(current, currsch);

        Plan p;
        if (hashPlan != null && indexPlan != null) {
            // 両側が大きい (blocksAccessed > 20) → HashJoin (逐次 I/O が有利)
            // 外側が小さい → IndexJoin (少ない random lookupで済む)
            // recordsOutput() は統計推定が不正確なため blocksAccessed() で判定する
            // 20 は暫定値で実測の根拠はない（4KB ブロックで約 80KB）。
            // 本来は両 Plan のコスト比較だが、外側行数の統計が不正確なため据え置く
            boolean outerLarge = current.blocksAccessed() > 20;
            boolean innerLarge = myplan.blocksAccessed()  > 20;
            p = (outerLarge && innerLarge) ? hashPlan : indexPlan;
        } else if (hashPlan != null) {
            p = hashPlan;
        } else if (indexPlan != null) {
            p = indexPlan;
        } else {
            p = makeProductJoin(current, currsch);
        }
        return p;
    }

    /**
     * product planを作る
     * 
     * @param current
     * @return product plan
     */
    public Plan makeProductPlan(Plan current) {
        Plan p = addSelectPred(myplan);
        return new MultiBufferProductPlan(current, p, tx);
    }

    // 等値条件に合う index のうち最安のものを選び、seq scan より安いときだけ Index Select にする
    private Plan makeIndexSelect() {
        if (!useIndex) return null;
        IndexInfo best = null;
        Constant bestval = null;
        double bestcost = 0;
        // フィールド名順に見る（同コストなら先に見たもの。HashMap の順に依らないようにする）
        for (String fldname : new TreeSet<>(indexes.keySet())) {
            Constant val = mypred.equatesWithConstant(fldname);
            if (val == null)
                continue;
            IndexInfo ii = indexes.get(fldname);
            double cost = indexCost(ii.blocksAccessed(), ii.recordsOutput());
            if (best == null || cost < bestcost) {
                best = ii;
                bestval = val;
                bestcost = cost;
            }
        }
        if (best == null)
            return null;
        if (!preferIndex(myplan.blocksAccessed(), best.blocksAccessed(), best.recordsOutput()))
            return null;
        return new IndexSelectPlan(myplan, best, bestval);
    }

    // index scan の推定コスト。index ページは逐次、親表の行取得はランダムとみなす。
    // index ページは最低 1 ブロック読むので下限 1（Hash の searchCost は小さい表で 0 になる）
    static double indexCost(int idxBlocks, int rows) {
        return W_SEQ * Math.max(1, idxBlocks) + W_RANDOM * rows;
    }

    // index scan が seq scan 以下のコストなら true（同点は index）
    // seqBlocks が 0 以下（統計が空）のときは比較せず true（従来どおり index を使う）
    static boolean preferIndex(int seqBlocks, int idxBlocks, int rows) {
        if (seqBlocks <= 0)
            return true;
        return indexCost(idxBlocks, rows) <= W_SEQ * seqBlocks;
    }

    /**
     * index列の角属性について、それが外部表にも属性があるなら
     * (F2 = indexes.F1 かつ　currschがF2を持つ)
     * IndexJoinPlanを作る
     * 
     * @param current
     * @param currsch
     * @return
     */
    private Plan makeIndexJoin(Plan current, Schema currsch) {
        if (!useIndex) return null;
        for (String fldname : indexes.keySet()) {
            String outerfield = mypred.equatesWithField(fldname);
            if (outerfield != null && currsch.hasField(outerfield)) {
                IndexInfo ii = indexes.get(fldname);
                Plan p = new IndexJoinPlan(current, myplan, ii, outerfield);
                p = addSelectPred(p);
                return addJoinPred(p, currsch);
            }
        }
        return null;
    }

    private Plan makeHashJoin(Plan current, Schema currsch) {
        for (String fldname : myschema.fields()) {
            String outerFld = mypred.equatesWithField(fldname);
            if (outerFld != null && currsch.hasField(outerFld)) {
                Plan rhs = addSelectPred(myplan);
                Plan p = new HashJoinPlan(current, rhs, outerFld, fldname, tx);
                return addJoinPred(p, currsch);
            }
        }
        return null;
    }

    private Plan makeProductJoin(Plan current, Schema currsch) {
        Plan p = makeProductPlan(current);
        return addJoinPred(p, currsch);
    }

    private Plan addSelectPred(Plan p) {
        Predicate selectpred = mypred.selectSubPred(myschema);
        if (selectpred != null)
            return new SelectPlan(p, selectpred);
        else
            return p;
    }

    private Plan addJoinPred(Plan p, Schema currsch) {
        Predicate joinpred = mypred.joinSubPred(currsch, myschema);
        if (joinpred != null)
            return new SelectPlan(p, joinpred);
        else
            return p;
    }
}
