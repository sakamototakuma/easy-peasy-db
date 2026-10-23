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
        TableScan ts = new TableScan(tx, "idxcat", layout);
        ts.insert();
        ts.setString("indexname", idxname);
        ts.setString("tablename", tblname);
        ts.setString("fieldname", fldname);
        ts.setString("indextype", indextype);
        ts.close();
    }

    public Map<String, IndexInfo> getIndexInfo(String tblname, Transaction tx) {
        Map<String,IndexInfo> result = new HashMap<String,IndexInfo>();
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
            result.put(fldname, ii);
        }
        ts.close();
        return result;
    }
}
