package esypsydb.materialize;

import esypsydb.plan.Plan;
import esypsydb.query.Scan;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

import java.util.List;

/**
 * Hash Join プラン。
 *
 * p1 (lhs) をビルド側 (ハッシュテーブルへ格納)、
 * p2 (rhs) をプローブ側 (逐次スキャン) として Hash Join を実行する。
 *
 * コスト: p1.blocksAccessed() + p2.blocksAccessed()  (積結合の O(N*M) に対し O(N+M))
 */
public class HashJoinPlan implements Plan {
    private final Plan p1;
    private final Plan p2;
    private final String lhsField;
    private final String rhsField;
    private final Transaction tx;
    private final Schema sch;

    public HashJoinPlan(Plan p1, Plan p2,
                        String lhsField, String rhsField,
                        Transaction tx) {
        this.p1        = p1;
        this.p2        = p2;
        this.lhsField  = lhsField;
        this.rhsField  = rhsField;
        this.tx        = tx;
        this.sch       = new Schema();
        sch.addAll(p1.schema());
        sch.addAll(p2.schema());
    }

    @Override
    public Scan open() {
        return new HashJoinScan(p1, p2, lhsField, rhsField, tx);
    }

    @Override
    public int blocksAccessed() {
        return p1.blocksAccessed() + p2.blocksAccessed();
    }

    @Override
    public int recordsOutput() {
        int dv = Math.max(1,
                    Math.max(p1.distinctValues(lhsField),
                             p2.distinctValues(rhsField)));
        return p1.recordsOutput() * p2.recordsOutput() / dv;
    }

    @Override
    public int distinctValues(String fldname) {
        if (p1.schema().hasField(fldname))
            return p1.distinctValues(fldname);
        else
            return p2.distinctValues(fldname);
    }

    @Override
    public Schema schema() {
        return sch;
    }

    @Override
    public String nodeTypeName() {
        return "Hash Join";
    }

    @Override
    public List<String> extraInfoLines() {
        return List.of("Hash Cond: (" + lhsField + " = " + rhsField + ")");
    }

    public String accessMethod() {
        return "hash-join(lhsField=" + lhsField + ", rhsField=" + rhsField + ")";
    }
}
