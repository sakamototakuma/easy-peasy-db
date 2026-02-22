package esypsydb.query;

import esypsydb.record.RID;

/**
 * 条件（Predicate）に一致するレコードだけを返す更新可能スキャン。
 *
 * 設計メモ:
 *   - SelectScan（読み取り専用）を継承し、UpdateScan も実装する。
 *   - コンストラクタで UpdateScan を受け取るため、
 *     コンパイル時に「下位スキャンが更新可能か」が保証される
 */
public class SelectUpdateScan extends SelectScan implements UpdateScan {
    private final UpdateScan us;

    public SelectUpdateScan(UpdateScan us, Predicate pred) {
        super(us, pred); // 親の Scan s にも同じインスタンスを渡す
        this.us = us;
    }

    @Override
    public void setInt(String fldname, int val) {
        us.setInt(fldname, val);
    }

    @Override
    public void setString(String fldname, String val) {
        us.setString(fldname, val);
    }

    @Override
    public void setVal(String fldname, Constant val) {
        us.setVal(fldname, val);
    }

    @Override
    public void insert() {
        us.insert();
    }

    @Override
    public void delete() {
        us.delete();
    }

    @Override
    public RID getRid() {
        return us.getRid();
    }

    @Override
    public void moveToRid(RID rid) {
        us.moveToRid(rid);
    }
}
