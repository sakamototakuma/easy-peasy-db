package esypsydb.opt;

import java.util.*;

import esypsydb.metadata.MetadataMgr;
import esypsydb.parse.QueryData;
import esypsydb.plan.Plan;
import esypsydb.plan.ProjectPlan;
import esypsydb.plan.QueryPlanner;
import esypsydb.tx.Transaction;


public class HeuristicQueryPlanner implements QueryPlanner {
    private final MetadataMgr mdm;
    private final boolean useIndex;
    private Collection<TablePlanner> tableplanners = new ArrayList<TablePlanner>();

    public HeuristicQueryPlanner(MetadataMgr mdm) {
        this(mdm, true);
    }

    public HeuristicQueryPlanner(MetadataMgr mdm, boolean useIndex) {
        this.mdm = mdm;
        this.useIndex = useIndex;
    }

    @Override
    public Plan createPlan(QueryData data, Transaction tx) {
        tableplanners.clear();

        // Step1: FROM句の各テーブルのTablePlannerオブジェクトを作成
        for (String tblname : data.tables()) {
            TablePlanner tp = new TablePlanner(tblname, data.pred(), tx, mdm, useIndex);
            tableplanners.add(tp);
        }

        // Step2: 最初のjoin orderの先頭を決める. 最も制約の強い選択述語 heuristic 5b
        Plan currentplan = getLowestSelectPlan();

        // Step3: joinできる表があるなら、繰り返しjoin orderに最良のplanを追加する
        //        どの表ともjoin predicateがないならproduct
        while (!tableplanners.isEmpty()) {
            Plan p = getLowestJoinPlan(currentplan);
            if (p != null)
                currentplan = p;
            else
                currentplan = getLowestProductPlan(currentplan);
            
        }
        
        // Step4: 最後にProject
        return new ProjectPlan(currentplan, data.fields());
    }

    /**
     * Heuristic 5b: 各表の selectivity = recordsOutput / rawOutput を計算し、
     * 最も制約の強い（selectivity 最小）選択述語を持つ表を選ぶ。
     * 述語のない表は selectivity = 1.0 となり後回しになる。
     *
     * Heuristic 5a（最小 recordsOutput で選択）
     */
    private Plan getLowestSelectPlan() {
        TablePlanner besttp = null;
        Plan bestplan = null;
        double bestSelectivity = Double.MAX_VALUE;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeSelectPlan();
            int raw = tp.rawOutput();
            double selectivity = (raw == 0) ? 0.0 : (double) plan.recordsOutput() / raw;
            if (bestplan == null || selectivity < bestSelectivity) {
                besttp = tp;
                bestplan = plan;
                bestSelectivity = selectivity;
            }
        }
        tableplanners.remove(besttp);
        return bestplan;
    }

    private Plan getLowestJoinPlan(Plan current) {
        TablePlanner besttp = null;
        Plan bestplan = null;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeJoiPlan(current);
            if (plan != null && (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput())) {
                besttp = tp;
                bestplan = plan;
            }
        }
        if (bestplan != null)
            tableplanners.remove(besttp);
        return bestplan;
    }

    private Plan getLowestProductPlan(Plan current) {
        TablePlanner besttp = null;
        Plan bestplan = null;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeProductPlan(current);
            if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
                besttp = tp;
                bestplan = plan;
            }
        }
        tableplanners.remove(besttp);
        return bestplan;
    } 
}
