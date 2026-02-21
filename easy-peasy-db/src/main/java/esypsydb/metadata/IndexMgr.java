package esypsydb.metadata;

import java.util.*;
import esypsydb.record.*;
import static esypsydb.metadata.TableMgr.MAX_NAME;
import esypsydb.tx.Transaction;

public class IndexMgr {
    private Layout layout;
    private TableMgr tableMgr;
    private StatMgr statMgr;

    public IndexMgr(boolean isnew, TableMgr tblmgr, StatMgr statmgr, Transaction tx) {
        if (isnew) {
            Schema sch = new Schema();
            sch.addStringField("indexname", MAX_NAME);
            sch.addStringField("tablename", MAX_NAME);
            sch.addStringField("fieldname", MAX_NAME);
            tblmgr.createTable("idxcat", sch, tx);
        }
        this.tblmgr = tblmgr;
        this.statmgr = statmgr;
        layout = tblmgr.getLayout("idxcat", tx);
    }

    public void createIndex(String idxname, String tblname, 
                            String fldname, Transaction tx) {
        TableScan ts = new TableScan(tx, "idxcat", layout);
        ts.insert();
        ts.setString("indexname", idxname);
        ts.setString("tablename", tblname);
        ts.setString("fieldname", fldname);
        ts.close();
    }

    public Map<String, IndexInfo> getIndexInfo(String tblname, Transaction tx) {
        Map<String,IndexInfo> result = new HashMap<String,IndexInfo>();
        TableScan ts = new TableScan(tx, "idxcat", layout);
        while (ts.next())
            if (ts.getString("tablename").equals(tblname)) {
            String idxname = ts.getString("indexname");
            String fldname = ts.getString("fieldname");
            Layout tblLayout = tblmgr.getLayout(tblname, tx);
            StatInfo tblsi = statmgr.getStatInfo(tblname, tblLayout, tx);
            IndexInfo ii = new IndexInfo(idxname, fldname, tblLayout.schema(), tx, tblsi);
            result.put(fldname, ii);
        }
        ts.close();
        return result;
    }
}
