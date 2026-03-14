package esypsydb.index.query;

import esypsydb.index.Index;
import esypsydb.query.Constant;
import esypsydb.record.RID;
import esypsydb.record.TableScan;

public class IndexSelectScan {
    private TableScan ts;
    private Index idx;
    private Constant val;

    public IndexSelectScan(TableScan ts, Index idx, Constant val) {
        this.ts = ts;
        this.idx = idx;
        this.val = val;
        beforeFirst();
    }

    // 最初のレコードの直線にカーソルセット
    public void beforeFirst() {
        idx.beforeFirst(val);
    }

    // リーフ内でのscan
    public boolean next() {
        boolean ok = idx.next();    // index record を次の一致キーへ進める
        if (ok) {
            RID rid = idx.getDataRid(); // 本体recordのRIDを取る
            ts.moveToRid(rid);          // 本体 table scan を、そのRIDの行へ直接ジャンプ
        }
        return ok;
    }

    public int getInt(String fldname) {
        return ts.getInt(fldname);
    }

    public String getString(String fldname) {
        return ts.getString(fldname);
    }

    public Constant getVal(String fldname) {
        return ts.getVal(fldname);
    }

    public boolean hasField(String fldname) {
        return ts.hasField(fldname);
    }

    public void close() {
        idx.close();
        ts.close();
    }
}
