package esypsydb.record;

import static java.sql.Types.INTEGER;

import esypsydb.file.BlockId;
import esypsydb.query.Constant;
import esypsydb.query.Constant;
import esypsydb.tx.Transaction;

public class TableScan {
    private Transaction tx;
    private Layout layout;
    private RecordPage rp;
    private String filename;
    private int currentslot;
    public TableScan(Transaction tx, String tblname, Layout layout) {
        this.tx = tx;
        this.layout = layout;
        filename = tblname + ".tbl";
        if (tx.size(filename) == 0) 
            moveToNewBlock();
        else
            moveToBlock(0);
    }

    // ページを閉じる（unpinする）
    public void close() {
        if (rp != null) {
            tx.unpin(rp.block());
        }
    }

    public void beforeFirst() {
        moveToBlock(0);
    }

    public boolean next() {
        currentslot = rp.nextAfter(currentslot);
        while (currentslot < 0) {
            if (atLastBlock())
                return false;
            moveToBlock(rp.block().number()+1);
            currentslot = rp.nextAfter(currentslot); 
        }
        return true;
    }

    // 指定した列の値を型を限定して取り出す
    public int getInt(String fldname) {
        return rp.getInt(currentslot, fldname);
    }

    public String getString(String fldname) {
        return rp.getString(currentslot, fldname);
    }

    // 値をConstant型として取り出す
    public Constant getVal(String fldname) {
        if (layout.schema().type(fldname) == INTEGER)
            return new Constant(getInt(fldname));
        else
            return new Constant(getString(fldname));
    }

    // その列名がテーブルに存在するか
    public boolean hasField(String fldname) {
        return layout.schema().hasField(fldname);
    }

    public void setInt(String fldname, int val) {
        rp.setInt(currentslot, fldname, val);
    }

    public void setString(String fldname, String val) {
        rp.setString(currentslot, fldname, val);
    }

    // 整数/文字列フィールドの値を書き込む
    public void setVal(String fldname, Constant val) {
        if (layout.schema().type(fldname) == INTEGER)
            setInt(fldname, (Integer)val.asInt());
        else
            setString(fldname, (String)val.asString());
    }

    /**
     * 新しいレコードを挿入する「空きスロット」を確保し、currentslot をそこへ移動する
     *
     *   正しい手順:
     *       ts.insert();          ← 先に空きスロットを確保
     *       ts.setString("col", value);
     *       ts.setInt("col2", value2);
     */
    public void insert() {
        currentslot = rp.insertAfter(currentslot);
        while (currentslot < 0) {
            if (atLastBlock())
                moveToNewBlock();
            else
                moveToBlock(rp.block().number()+1);
            currentslot = rp.insertAfter(currentslot);
        }
    }

    public void delete() {
        rp.delete(currentslot);
    }

    public void moveToRid(RID rid) {
        close();
        BlockId blk = new BlockId(filename, rid.blockNumber());
        rp = new RecordPage(tx, blk, layout);
        currentslot = rid.slot();
    }

    // RIDの取得
    public RID getRid() {
        return new RID(rp.block().number(), currentslot);
    }

    private void moveToBlock(int blknum) {
        close();
        BlockId blk = new BlockId(filename, blknum);
        rp = new RecordPage(tx, blk, layout);
        currentslot = -1;
    }

    private void moveToNewBlock() {
        close();
        BlockId blk = tx.append(filename);
        rp = new RecordPage(tx, blk, layout);
        rp.format();
        currentslot = -1;
    }

    // 今いる場所がファイル最後か判定する
    private boolean atLastBlock() {
        return rp.block().number() == tx.size(filename) -1;
    }

}
