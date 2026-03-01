package esypsydb.plan;

import esypsydb.metadata.MetadataMgr;
import esypsydb.parse.*;
import esypsydb.query.Constant;
import esypsydb.query.UpdateScan;
import esypsydb.tx.Transaction;
import java.util.*;

public class BasicUpdatePlanner implements UpdatePlanner {
    private MetadataMgr mdm;
   
   public BasicUpdatePlanner(MetadataMgr mdm) {
      this.mdm = mdm;
   }
   
   public int executeDelete(DeleteData data, Transaction tx) {
      Plan p = new TablePlan(tx, data.tableName(), mdm);
      p = new SelectPlan(p, data.pred());
      UpdateScan us = (UpdateScan) p.open();
      int count = 0;
      while(us.next()) {
         us.delete();
         count++;
      }
      us.close();
      return count;
   }
   
   public int executeModify(ModifyData data, Transaction tx) {
      Plan p = new TablePlan(tx, data.tableName(), mdm);
      p = new SelectPlan(p, data.pred());
      UpdateScan us = (UpdateScan) p.open();
      int count = 0;
      while(us.next()) {
         Constant val = data.newValue().evaluate(us);
         us.setVal(data.targetField(), val);
         count++;
      }
      us.close();
      return count;
   }
   
   public int executeInsert(InsertData data, Transaction tx) {
      Plan p = new TablePlan(tx, data.tableName(), mdm);
      UpdateScan us = (UpdateScan) p.open();
      us.insert();
      Iterator<Constant> iter = data.vals().iterator();
      for (String fldname : data.fields()) {
         Constant val = iter.next();
         us.setVal(fldname, val);
      }
      us.close();
      return 1;
   }

   // CREATION系はplanを作る必要はない！

   public int executeCreateTable(CreateTableData data, Transaction tx) {
      mdm.createTable(data.tableName(), data.newSchema(), tx);
      return 0;
   }
   
   public int executeCreateView(CreateViewData data, Transaction tx) {
      mdm.createView(data.viewName(), data.viewDef(), tx);
      return 0;
   }
   public int executeCreateIndex(CreateIndexData data, Transaction tx) {
      mdm.createIndex(data.indexName(), data.tableName(), data.fieldName(), tx);
      return 0;  
   }
}
