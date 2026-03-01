package esypsydb.plan;

import esypsydb.parse.QueryData;
import esypsydb.tx.Transaction;

public interface QueryPlanner {

    /**
     * 
     * @param data
     * @param tx
     * @return
     */
    public Plan createPlan(QueryData data, Transaction tx);
}
