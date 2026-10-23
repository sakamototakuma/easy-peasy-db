package esypsydb.metadata;

import java.util.*;
import esypsydb.record.*;
import static esypsydb.metadata.TableMgr.MAX_NAME;
import esypsydb.tx.Transaction;

public class IndexMgr {
    private static final int MAX_TYPE = 8;
    private static final String OLD_FORMAT_MSG =
        "idxcat に indextype 列がありません。旧形式の DB は再作成してください";

    private Layout layout;
    private TableMgr tblmgr;
    private StatMgr statmgr;

    public IndexMgr(boolean isnew, TableMgr tblmgr, StatMgr statmgr, Transaction tx) {
        if (isnew) {
            Schema sch = new Schema();
            sch.addStringField("indexname", MAX_NAME);
            sch.addStringField("tablename", MAX_NAME);
            sch.addStringField("fieldname", MAX_NAME);
            sch.addStringField("indextype", MAX_TYPE);
            tblmgr.createTable("idxcat", sch, tx);
        }
        this.tblmgr = tblmgr;
        this.statmgr = statmgr;
        layout = tblmgr.getLayout("idxcat", tx);
    }

    public void createIndex(String idxname, String tblname,
                            String fldname, String indextype, Transaction tx) {
        if (!layout.schema().hasField("indextype"))
            throw new IllegalStateException(OLD_FORMAT_MSG);
        if (!IndexInfo.isKnownType(indextype))
            throw new IllegalArgumentException("unknown index type: " + indextype);
        // 同名は index ファイル名が衝突する。同じ (表, 列, 種別) の重複も作らない
        TableScan dup = new TableScan(tx, "idxcat", layout);
        while (dup.next()) {
            boolean sameName = dup.getString("indexname").equals(idxname);
            boolean sameSpec = dup.getString("tablename").equals(tblname)
                            && dup.getString("fieldname").equals(fldname)
                            && dup.getString("indextype").equals(indextype);
            if (sameName || sameSpec) {
                dup.close();
                throw new IllegalArgumentException(sameName
                    ? "index already exists: " + idxname
                    : "same index already exists on " + tblname + "." + fldname + " (" + indextype + ")");
            }
        }
        dup.close();
        TableScan ts = new TableScan(tx, "idxcat", layout);
        ts.insert();
        ts.setString("indexname", idxname);
        ts.setString("tablename", tblname);
        ts.setString("fieldname", fldname);
        ts.setString("indextype", indextype);
        ts.close();
    }

    // フィールドごとに最安の index を 1 本返す（プランナ用）。
    // 同一フィールドの候補では recordsOutput() も index ページの重みも共通なので、
    // blocksAccessed() の比較だけで選んでよい
    public Map<String, IndexInfo> getIndexInfo(String tblname, Transaction tx) {
        Map<String,IndexInfo> result = new HashMap<String,IndexInfo>();
        for (IndexInfo ii : getAllIndexInfo(tblname, tx)) {
            IndexInfo cur = result.get(ii.fieldName());
            if (cur == null
                    || prefer(cur.blocksAccessed(), cur.isHash(), ii.blocksAccessed(), ii.isHash()))
                result.put(ii.fieldName(), ii);
        }
        return result;
    }

    // 新しい候補が今の候補より安ければ true。コストは max(1, blocksAccessed())（Hash は小さい表で 0 を返すため）。
    // 同点は Hash を残す（更新が軽い）。idxcat の行の順に結果が依らない
    static boolean prefer(int curBlocks, boolean curIsHash, int newBlocks, boolean newIsHash) {
        int curCost = Math.max(1, curBlocks);
        int newCost = Math.max(1, newBlocks);
        if (newCost != curCost)
            return newCost < curCost;
        return newIsHash && !curIsHash;
    }

    // 表の全 index（同一フィールドに複数あってもすべて返す）。更新系はこちらを使う
    public List<IndexInfo> getAllIndexInfo(String tblname, Transaction tx) {
        List<IndexInfo> result = new ArrayList<IndexInfo>();
        TableScan ts = new TableScan(tx, "idxcat", layout);
        while (ts.next())
            if (ts.getString("tablename").equals(tblname)) {
            if (!layout.schema().hasField("indextype")) {
                ts.close();
                throw new IllegalStateException(OLD_FORMAT_MSG);
            }
            String idxname = ts.getString("indexname");
            String fldname = ts.getString("fieldname");
            String idxtype = ts.getString("indextype");
            Layout tblLayout = tblmgr.getLayout(tblname, tx);
            StatInfo tblsi = statmgr.getStatInfo(tblname, tblLayout, tx);
            IndexInfo ii = new IndexInfo(idxname, fldname, idxtype, tblLayout.schema(), tx, tblsi);
            result.add(ii);
        }
        ts.close();
        return result;
    }
}
