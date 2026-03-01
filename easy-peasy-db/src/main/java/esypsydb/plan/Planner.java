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

    public Plan creaateQueryPlan(String cmd, Transaction tx) {
        Parser parser = new Parser(cmd);
        QueryData data = parser.query();  // クエリをパース
        // クエリ検証のコードをここに
        return qPlanner.createPlan(data, tx);
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
