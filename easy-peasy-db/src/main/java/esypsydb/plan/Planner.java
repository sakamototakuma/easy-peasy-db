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
        QueryData data = parser.query(); // クエリをパース
        // クエリ検証のコードをここに
        return qPlanner.createPlan(data, tx);
    }

    /**
     * "explain [analyze] select ..." を受け取り、プラン木を文字列で返す
     * <ul>
     * <li>{@code explain select ...} — 見積もりのみ（PostgreSQL の EXPLAIN 相当）</li>
     * <li>{@code explain analyze select ...} — 実行して実測値付き
     * （PostgreSQL の EXPLAIN ANALYZE 相当）</li>
     * </ul>
     */
    public String explainQuery(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        parser.eatExplain();
        boolean analyze = parser.eatAnalyze();
        QueryData data = parser.query();
        // ── プラン作成（Planning Time 計測） ──
        long planStart = System.nanoTime();
        Plan plan = qPlanner.createPlan(data, tx);
        long planNs = System.nanoTime() - planStart;

        if (analyze) {
            // ── EXPLAIN ANALYZE: 実行して実測値を収集 ──
            InstrumentedPlan root = InstrumentedPlan.instrument(plan);
            long execNs = root.execute();

            return PlanFormatter.formatAnalyze(root, planNs, execNs);
        } else {
            // ── EXPLAIN: 見積もりのみ ──
            return PlanFormatter.format(plan);
        }
    }

    public int executeUpdate(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        Object obj = parser.updateCmd();
        // クエリ検証のコードをここに
        // INSERT
        if (obj instanceof InsertData)
            return uplanner.executeInsert((InsertData) obj, tx);
        // DELETE
        else if (obj instanceof DeleteData)
            return uplanner.executeDelete((DeleteData) obj, tx);
        // UPDSTE
        else if (obj instanceof ModifyData)
            return uplanner.executeModify((ModifyData) obj, tx);
        // CREATE TABLE
        else if (obj instanceof CreateTableData)
            return uplanner.executeCreateTable((CreateTableData) obj, tx);
        // CREATE VIEW
        else if (obj instanceof CreateViewData)
            return uplanner.executeCreateView((CreateViewData) obj, tx);
        // CREATE INDEX
        else if (obj instanceof CreateIndexData)
            return uplanner.executeCreateIndex((CreateIndexData) obj, tx);
        else
            return 0;
    }
}
