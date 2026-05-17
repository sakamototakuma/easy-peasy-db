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



public class TablePlanner {
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
        // HashJoin を優先: 統計モデルが外側行数を過小評価するため
        // IndexJoin の「外側×ランダム検索」コストが不正確になる。
        Plan p = makeHashJoin(current, currsch);
        if (p == null)
            p = makeIndexJoin(current, currsch);
        if (p == null)
            p = makeProductJoin(current, currsch);
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

    private Plan makeIndexSelect() {
        if (!useIndex) return null;
        for (String fldname : indexes.keySet()) {
            Constant val = mypred.equatesWithConstant(fldname);
            if (val != null) {
                IndexInfo ii = indexes.get(fldname);
                return new IndexSelectPlan(myplan, ii, val);
            }
        }
        return null;
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
