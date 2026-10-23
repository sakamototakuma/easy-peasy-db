package esypsydb.index;

import java.util.ArrayList;
import java.util.List;

import esypsydb.metadata.IndexInfo;
import esypsydb.metadata.MetadataMgr;
import esypsydb.plan.Plan;
import esypsydb.plan.TablePlan;
import esypsydb.query.Constant;
import esypsydb.query.UpdateScan;
import esypsydb.record.RID;
import esypsydb.server.EasyPeasyDB;
import esypsydb.tx.Transaction;

public class IndexUpdateTest {
       public static void main(String[] args) {
      EasyPeasyDB db = new EasyPeasyDB("studentdb");
      Transaction tx = db.newTx();
      MetadataMgr mdm = db.mdMgr();
      Plan studentplan = new TablePlan(tx, "student", mdm);
      UpdateScan studentscan = (UpdateScan) studentplan.open();

      // Open every index for STUDENT (a field may have several).
      List<IndexInfo> infos = mdm.getAllIndexInfo("student", tx);
      List<Index> indexes = new ArrayList<>();
      for (IndexInfo ii : infos)
         indexes.add(ii.open());

      // Task 1: insert a new STUDENT record for Sam
      //    First, insert the record into STUDENT.
      studentscan.insert();
      studentscan.setInt("sid", 11);
      studentscan.setString("sname", "sam");
      studentscan.setInt("gradyear", 2023);
      studentscan.setInt("majorid",  30);

      //    Then insert a record into each of the indexes.
      RID datarid = studentscan.getRid();
      for (int i = 0; i < infos.size(); i++) {
         Constant dataval = studentscan.getVal(infos.get(i).fieldName());
         indexes.get(i).insert(dataval, datarid);
      }

      // Task 2: find and delete Joe's record
      studentscan.beforeFirst();
      while (studentscan.next()) {
         if (studentscan.getString("sname").equals("joe")) {

            // First, delete the index records for Joe.
            RID joeRid = studentscan.getRid();
            for (int i = 0; i < infos.size(); i++) {
               Constant dataval = studentscan.getVal(infos.get(i).fieldName());
               indexes.get(i).delete(dataval, joeRid);
            }

            // Then delete Joe's record in STUDENT.
            studentscan.delete();
            break;
         }
      }
      
      // Print the records to verify the updates.
      studentscan.beforeFirst();
      while (studentscan.next()) {
         System.out.println(studentscan.getString("sname") + " " + studentscan.getInt("sid"));
      }
      studentscan.close();
      
      for (Index idx : indexes)
         idx.close();
      tx.commit();
   }
}
