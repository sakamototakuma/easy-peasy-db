package esypsydb.materialize;

import esypsydb.index.hash.ExtendibleHashTable;
import esypsydb.plan.Plan;
import esypsydb.query.Constant;
import esypsydb.query.Scan;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

import java.util.HashMap;
import java.util.Map;

/**
 * Hash Join スキャン。
 *
 * ビルドフェーズ: lhs (小さい側) を ExtendibleHashTable に格納。
 * プローブフェーズ: rhs を逐次スキャンし、各行のジョインキーでハッシュテーブルを検索。
 */
public class HashJoinScan implements Scan {
    private final Scan rhsScan;
    private final ExtendibleHashTable hashTable;
    private final String lhsField;
    private final String rhsField;
    private final Schema lhsSchema;
    private Map<String, Constant> currentLhsRow;
    private boolean initialized = false;

    public HashJoinScan(Plan lhs, Plan rhs,
                        String lhsField, String rhsField,
                        Transaction tx) {
        this.lhsField  = lhsField;
        this.rhsField  = rhsField;
        this.lhsSchema = lhs.schema();

        // ビルドフェーズ: lhs を全行スキャンしてハッシュテーブルへ投入
        this.hashTable = new ExtendibleHashTable(tx, lhs.schema(), lhsField);
        Scan s = lhs.open();
        s.beforeFirst();
        while (s.next()) {
            Map<String, Constant> row = new HashMap<>();
            for (String f : lhs.schema().fields())
                row.put(f, s.getVal(f));
            hashTable.insert(s.getVal(lhsField), row);
        }
        s.close();

        // プローブ準備: rhs スキャンを開く
        this.rhsScan = rhs.open();
        this.rhsScan.beforeFirst();
    }

    @Override
    public void beforeFirst() {
        rhsScan.beforeFirst();
        initialized = false;
    }

    @Override
    public boolean next() {
        if (!initialized) {
            initialized = true;
            if (!rhsScan.next()) return false;
            hashTable.beforeFirst(rhsScan.getVal(rhsField));
        }
        while (true) {
            if (hashTable.next()) {
                currentLhsRow = hashTable.getRow();
                return true;
            }
            if (!rhsScan.next()) return false;
            hashTable.beforeFirst(rhsScan.getVal(rhsField));
        }
    }

    @Override
    public Constant getVal(String fldname) {
        if (rhsScan.hasField(fldname))
            return rhsScan.getVal(fldname);
        return currentLhsRow.get(fldname);
    }

    @Override
    public int getInt(String fldname) {
        return getVal(fldname).asInt();
    }

    @Override
    public String getString(String fldname) {
        return getVal(fldname).asString();
    }

    @Override
    public boolean hasField(String fldname) {
        return rhsScan.hasField(fldname) || lhsSchema.hasField(fldname);
    }

    @Override
    public void close() {
        rhsScan.close();
        hashTable.close();
    }
}
