package esypsydb.index;

import java.util.Map;

import esypsydb.metadata.IndexInfo;
import esypsydb.metadata.MetadataMgr;
import esypsydb.plan.Plan;
import esypsydb.plan.TablePlan;
import esypsydb.query.Constant;
import esypsydb.query.UpdateScan;
import esypsydb.record.RID;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class IndexRetrivalTest {
    public static void main(String[] args) {
      EasyPeasyDB db = new EasyPeasyDB("studentdb");
      Transaction tx = db.newTx();
      MetadataMgr mdm = db.mdMgr();

      // Open a scan on the data table.
      Plan studentplan = new TablePlan(tx, "student", mdm);
      UpdateScan studentscan = (UpdateScan) studentplan.open();

      // Open the index on MajorId.
      Map<String,IndexInfo> indexes = mdm.getIndexInfo("student", tx);
      IndexInfo ii = indexes.get("majorid");
      Index idx = ii.open();

      // Retrieve all index records having a dataval of 20.
      idx.beforeFirst(new Constant(20));
      while (idx.next()) {
         // Use the datarid to go to the corresponding STUDENT record.
         RID datarid = idx.getDataRid();
         studentscan.moveToRid(datarid);
         System.out.println(studentscan.getString("sname"));
      }

      // Close the index and the data table.
      idx.close();
      studentscan.close();
      tx.commit();
   }
}
