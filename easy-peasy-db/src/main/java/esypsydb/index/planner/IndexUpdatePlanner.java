package esypsydb.index.planner;

import java.util.*;

import esypsydb.index.Index;
import esypsydb.metadata.IndexInfo;
import esypsydb.metadata.MetadataMgr;
import esypsydb.parse.*;
import esypsydb.plan.*;
import esypsydb.query.*;
import esypsydb.record.RID;
import esypsydb.tx.Transaction;

public class IndexUpdatePlanner implements UpdatePlanner {
    private MetadataMgr mdm;
   
    public IndexUpdatePlanner(MetadataMgr mdm) {
        this.mdm = mdm;
    }
    
    public int executeInsert(InsertData data, Transaction tx) {
        String tblname = data.tableName();
        Plan p = new TablePlan(tx, tblname, mdm);
        
        // first, insert the record
        UpdateScan s = (UpdateScan) p.open();
        s.insert();
        RID rid = s.getRid();
        
        // then modify each field, inserting an index record into every index on that field
        List<IndexInfo> indexes = mdm.getAllIndexInfo(tblname, tx);
        Iterator<Constant> valIter = data.vals().iterator();
        for (String fldname : data.fields()) {
            Constant val = valIter.next();
            s.setVal(fldname, val);

            for (IndexInfo ii : indexes) {
                if (!ii.fieldName().equals(fldname))
                    continue;
                Index idx = ii.open();
                idx.insert(val, rid);
                idx.close();
            }
        }
        s.close();
        return 1;
    }
    
    public int executeDelete(DeleteData data, Transaction tx) {
        String tblname = data.tableName();
        Plan p = new TablePlan(tx, tblname, mdm);
        p = new SelectPlan(p, data.pred());
        List<IndexInfo> indexes = mdm.getAllIndexInfo(tblname, tx);

        UpdateScan s = (UpdateScan) p.open();
        int count = 0;
        while(s.next()) {
            // first, delete the record's RID from every index
            RID rid = s.getRid();
            for (IndexInfo ii : indexes) {
                Constant val = s.getVal(ii.fieldName());
                Index idx = ii.open();
                idx.delete(val, rid);
                idx.close();
            }
            // then delete the record
            s.delete();
            count++;
        }
        s.close();
        return count;
    }
    
    public int executeModify(ModifyData data, Transaction tx) {
        String tblname = data.tableName();
        String fldname = data.targetField();
        Plan p = new TablePlan(tx, tblname, mdm);
        p = new SelectPlan(p, data.pred());
        
        // 対象フィールドの index はすべて更新する（同一フィールドに複数ありうる）
        List<Index> idxs = new ArrayList<Index>();
        for (IndexInfo ii : mdm.getAllIndexInfo(tblname, tx))
            if (ii.fieldName().equals(fldname))
                idxs.add(ii.open());

        UpdateScan s = (UpdateScan) p.open();
        int count = 0;
        while(s.next()) {
            // first, update the record
            Constant newval = data.newValue().evaluate(s);
            Constant oldval = s.getVal(fldname);
            s.setVal(data.targetField(), newval);

            // then update every index on that field
            RID rid = s.getRid();
            for (Index idx : idxs) {
                idx.delete(oldval, rid);
                idx.insert(newval, rid);
            }
            count++;
        }
        for (Index idx : idxs)
            idx.close();
        s.close();
        return count;
    }
    
    public int executeCreateTable(CreateTableData data, Transaction tx) {
        mdm.createTable(data.tableName(), data.newSchema(), tx);
        return 0;
    }
    
    public int executeCreateView(CreateViewData data, Transaction tx) {
        mdm.createView(data.viewName(), data.viewDef(), tx);
        return 0;
    }
    
    public int executeCreateIndex(CreateIndexData data, Transaction tx) {
        String tblname = data.tableName();
        String fldname = data.fieldName();
        // 存在しない表・列には作らない（カタログに行を足す前に弾く）
        if (!mdm.getLayout(tblname, tx).schema().hasField(fldname))
            throw new IllegalArgumentException("no such field: " + tblname + "." + fldname);
        mdm.createIndex(data.indexName(), tblname, fldname, data.indexType(), tx);

        // 既存の行を新しい index に入れる（backfill）
        for (IndexInfo ii : mdm.getAllIndexInfo(tblname, tx)) {
            if (!ii.indexName().equals(data.indexName()))
                continue;
            UpdateScan s = (UpdateScan) new TablePlan(tx, tblname, mdm).open();
            Index idx = ii.open();
            while (s.next())
                idx.insert(s.getVal(fldname), s.getRid());
            idx.close();
            s.close();
        }
        return 0;
    }
}
