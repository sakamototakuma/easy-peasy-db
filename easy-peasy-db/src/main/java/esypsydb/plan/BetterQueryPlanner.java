package esypsydb.plan;

import java.util.*;

import esypsydb.metadata.MetadataMgr;
import esypsydb.parse.Parser;
import esypsydb.parse.QueryData;
import esypsydb.tx.Transaction;

public class BetterQueryPlanner implements QueryPlanner {
    private MetadataMgr mdm;

    public BetterQueryPlanner(MetadataMgr mdm) {
        this.mdm = mdm;
    }

    public Plan createPlan(QueryData data, Transaction tx) {
        // 1. 各参照テーブルorビューのプラン作成
        List<Plan> plans = new ArrayList<>();
        for (String tblname : data.tables()) {
            String viewdef = mdm.getViewDef(tblname, tx);
            if (viewdef != null) {
                Parser parser = new Parser(viewdef);
                QueryData viewdata = parser.query();
                plans.add(createPlan(viewdata, tx));
            }
            else 
                plans.add(new TablePlan(tx, tblname, mdm));
        }

        // 2. 　各プランの直積を与えられた順番通りに行う
        Plan p = plans.remove(0);
        for (Plan nextplan : plans) {
            Plan p1 = new ProductPlan(p, nextplan);
            Plan p2 = new ProductPlan(nextplan, p);
            // 直積の計算コストが小さい方を採用
            p =  p1.blocksAccessed() < p2.blocksAccessed() ? p1 : p2;
        }

        // 3. SelectPlan
        p = new SelectPlan(p, data.pred());

        // 4. ProjectPlan
        return new ProjectPlan(p, data.fields());
    }
}
