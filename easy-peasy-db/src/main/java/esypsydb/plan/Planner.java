package esypsydb.plan;

import esypsydb.parse.*;
import esypsydb.tx.Transaction;

public class Planner {
    private QueryPlanner qPlanner;
    private UpdatePlanner uplanner;

    public Planner(QueryPlanner qPlanner, UpdatePlanner uplanner) {
        this.qPlanner = qPlanner;
        this.uplanner = uplanner;
    }

    public Plan createQueryPlan(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        QueryData data = parser.query();  // クエリをパース
        // クエリ検証のコードをここに
        return qPlanner.createPlan(data, tx);
    }

    /**
     * "explain select ..." を受け取り、プラン木と実行時間を文字列で返す。
     * 先頭の "explain" キーワードは省略可（あっても無くても動く）。
     * 実際にプランを open() して全行走査し、経過時間を計測する。
     */
    public String explainQuery(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        parser.eatExplain();
        QueryData data = parser.query();
        Plan plan = qPlanner.createPlan(data, tx);

        long start = System.nanoTime();
        int rows = 0;
        esypsydb.query.Scan s = plan.open();
        try {
            while (s.next())
                rows++;
        } finally {
            s.close();
        }
        long elapsedNs = System.nanoTime() - start;

        StringBuilder sb = new StringBuilder();
        sb.append(PlanFormatter.format(plan));
        sb.append(String.format("Execution time: %.3f ms (actual rows=%d)%n",
                                elapsedNs / 1_000_000.0, rows));
        return sb.toString();
    }

    public int executeUpdate(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        Object obj = parser.updateCmd();
        // クエリ検証のコードをここに
        // INSERT
        if (obj instanceof InsertData)
            return uplanner.executeInsert((InsertData)obj, tx);
        // DELETE
        else if (obj instanceof DeleteData)
            return uplanner.executeDelete((DeleteData)obj, tx);
        // UPDSTE
        else if (obj instanceof ModifyData)
            return uplanner.executeModify((ModifyData)obj, tx);
        // CREATE TABLE
        else if (obj instanceof CreateTableData)
            return uplanner.executeCreateTable((CreateTableData)obj, tx);
        // CREATE VIEW
        else if (obj instanceof CreateViewData)
            return uplanner.executeCreateView((CreateViewData)obj, tx);
        // CREATE INDEX
        else if (obj instanceof CreateIndexData)
            return uplanner.executeCreateIndex((CreateIndexData)obj, tx);
        else
            return 0;
    }
}
