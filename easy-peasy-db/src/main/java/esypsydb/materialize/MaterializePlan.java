package esypsydb.materialize;

import esypsydb.plan.Plan;
import esypsydb.query.Scan;
import esypsydb.query.UpdateScan;
import esypsydb.record.Layout;
import esypsydb.record.Schema;
import esypsydb.tx.Transaction;

public class MaterializePlan implements Plan {
    private Plan srcplan;
    private Transaction tx;

    public MaterializePlan(Transaction tx, Plan srcplan) {
        this.tx = tx;
        this.srcplan = srcplan;
    }

    /*
     * materialize対象のsrcplanのschemaを取得し、TempTable作成
     * srcplan: 元のサブクエリを実行
     * tempを書き込み可能なScanとする
     * whileでsrcの内容をdestに書き込む
     * destのscan位置を先頭に移し、読み取りようにして返す
     * 
    */
    public Scan open() {
        Schema sch = srcplan.schema();
        TempTable temp = new TempTable(tx, sch);
        Scan src = srcplan.open();
        UpdateScan dest = temp.open();
        while (src.next()) {
            dest.insert();
            for (String fldname : sch.fields())
                dest.setVal(fldname, src.getVal(fldname));
        }
        src.close();            // srcplanは閉じる
        dest.beforeFirst();
        // TempTable を閉じられるよう MaterializeScan でラップ
        return new MaterializeScan(dest, temp);
    }

    public String scanName() {
        return "TableScan";
    }

    @Override
    public String nodeTypeName() {
        return "Materialize";
    }

    public String accessMethod() {
        return "materialize-to-temp-table";
    }

    public int blocksAccessed() {
        Layout layout = new Layout(srcplan.schema());
        double rpb = (double) (tx.blockSize() / layout.slotSize());
        return (int) Math.ceil(srcplan.recordsOutput() / rpb);
    }

    // mateliarizedテーブルの返すレコード総数
    public int recordsOutput() {
      return srcplan.recordsOutput();
   }

   public int distinctValues(String fldname) {
      return srcplan.distinctValues(fldname);
   }

   public Schema schema() {
      return srcplan.schema();
   }
}
