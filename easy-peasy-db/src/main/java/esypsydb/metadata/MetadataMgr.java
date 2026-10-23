package esypsydb.metadata;

import esypsydb.tx.Transaction;
import esypsydb.record.*;
import java.util.*;

public class MetadataMgr {
    private static TableMgr tblmgr;
    private static ViewMgr viewmgr;
    private static StatMgr statmgr;
    private static IndexMgr idxmgr;

    public MetadataMgr(boolean isnew, Transaction tx) {
        tblmgr = new TableMgr(isnew, tx);
        viewmgr = new ViewMgr(isnew, tblmgr, tx);
        statmgr = new StatMgr(tblmgr, tx);
        idxmgr = new IndexMgr(isnew, tblmgr, statmgr, tx);
    }

    public void createTable(String tblename, Schema sch, Transaction tx) {
        tblmgr.createTable(tblename, sch, tx);
    }

    public Layout getLayout(String tblname, Transaction tx) {
        return tblmgr.getLayout(tblname, tx);
    }

    public void createView(String viewname, String viewdef, Transaction tx) {
        viewmgr.createView(viewname, viewdef, tx);
    }

    public String getViewDef(String viewname, Transaction tx) {
        return viewmgr.getViewDef(viewname, tx);
    }

    // 種別を省略した呼び出しは Hash（従来どおり）
    public void createIndex(String idxname, String tblname, String fldname, Transaction tx) {
        createIndex(idxname, tblname, fldname, IndexInfo.HASH, tx);
    }

    public void createIndex(String idxname, String tblname, String fldname, String indextype, Transaction tx) {
        idxmgr.createIndex(idxname, tblname, fldname, indextype, tx);
    }

    public Map<String, IndexInfo> getIndexInfo(String tblname, Transaction tx) {
        return idxmgr.getIndexInfo(tblname, tx);
    }

    public StatInfo getStatInfo(String tblname, Layout layout, Transaction tx) {
        return statmgr.getStatInfo(tblname, layout, tx);
    }
}
