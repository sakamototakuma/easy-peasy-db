package esypsydb.materialize;

import esypsydb.query.Constant;
import esypsydb.query.Scan;
import esypsydb.query.UpdateScan;
import esypsydb.record.RID;

/**
 * MaterializePlan が作成した一時テーブルの Scan をラップし、
 * close() 時に TempTable のファイルハンドルを解放・削除する。
 */
public class MaterializeScan implements UpdateScan {
    private final UpdateScan wrapped;
    private final TempTable tempTable;

    public MaterializeScan(UpdateScan wrapped, TempTable tempTable) {
        this.wrapped = wrapped;
        this.tempTable = tempTable;
    }

    @Override
    public void beforeFirst() {
        wrapped.beforeFirst();
    }

    @Override
    public boolean next() {
        return wrapped.next();
    }

    @Override
    public int getInt(String fldname) {
        return wrapped.getInt(fldname);
    }

    @Override
    public String getString(String fldname) {
        return wrapped.getString(fldname);
    }

    @Override
    public Constant getVal(String fldname) {
        return wrapped.getVal(fldname);
    }

    @Override
    public boolean hasField(String fldname) {
        return wrapped.hasField(fldname);
    }

    // close() 時に TempTable.close() を呼ぶ
    @Override
    public void close() {
        wrapped.close();
        tempTable.close(); // FD 解放 + ファイル削除
    }

    // ── UpdateScan 委譲 ──

    @Override
    public void setVal(String fldname, Constant val) {
        wrapped.setVal(fldname, val);
    }

    @Override
    public void setInt(String fldname, int val) {
        wrapped.setInt(fldname, val);
    }

    @Override
    public void setString(String fldname, String val) {
        wrapped.setString(fldname, val);
    }

    @Override
    public void insert() {
        wrapped.insert();
    }

    @Override
    public void delete() {
        wrapped.delete();
    }

    @Override
    public RID getRid() {
        return wrapped.getRid();
    }

    @Override
    public void moveToRid(RID rid) {
        wrapped.moveToRid(rid);
    }
}
