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
    private Collection<TablePlanner> tableplanners = new ArrayList<TablePlanner>();

    public HeuristicQueryPlanner(MetadataMgr mdm) {
        this.mdm = mdm;
    }

    @Override
    public Plan createPlan(QueryData data, Transaction tx) {
        tableplanners.clear();

        // Step1: FROM句の各テーブルのTablePlannerオブジェクトを作成
        for (String tblname : data.tables()) {
            TablePlanner tp = new TablePlanner(tblname, data.pred(), tx, mdm);
            tableplanners.add(tp);
        }

        // Step2: 最初のjoin orderの先頭を決める. 最小の出力件数 heuristic 5a
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
     * 各表に対して makeSelectPlan()を行い、recordsOutput()が
     * 最小のものを選ぶ
     * 
     * @return
     */
    private Plan getLowestSelectPlan() {
        TablePlanner besttp = null;
        Plan bestplan = null;
        for (TablePlanner tp : tableplanners) {
            Plan plan = tp.makeSelectPlan();
            if (bestplan == null || plan.recordsOutput() < bestplan.recordsOutput()) {
                besttp = tp;
                bestplan = plan;
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
