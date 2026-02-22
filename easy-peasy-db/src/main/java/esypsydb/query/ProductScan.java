package esypsydb.query;

public class ProductScan implements Scan {
    private Scan s1, s2;

    // constructor
    public ProductScan(Scan s1, Scan s2) {
        this.s1 = s1;
        this.s2 = s2;
        s1.next();
    }

    // s2: 内側, s1: 外側 としこの順で進める
    @Override
    public void beforeFirst() {
        s1.beforeFirst();   // s1に先頭に
        s1.next();          // s1.next() で外側を1つ進め
        s2.beforeFirst();   // s2の先頭に
    }

    @Override
    public boolean next() {
        if (s2.next())      // 内側ループを進める
            return true;
        else {                              // s2 が終わったら
            s2.beforeFirst();               // 内側を先頭に戻し
            return s2.next() && s1.next();  // s2.next() ができれば true && s1も終わってたら false
        }
    }

    /*
    * どっちのscanの列かを s1.hasField(fldname) で判定して、
    *   - s1にあるならs1から取る
	*	- 無いならs2から取る
    */
    @Override
    public int getInt(String fldname) {
        if (s1.hasField(fldname))
            return s1.getInt(fldname);
        else
            return s2.getInt(fldname);
    }

    @Override
    public String getString(String fldname) {
        if (s1.hasField(fldname))
            return s1.getString(fldname);
        else
            return s2.getString(fldname);
    }

    @Override
    public Constant getVal(String fldname) {
        if (s1.hasField(fldname))
            return s1.getVal(fldname);
        else
            return s2.getVal(fldname);
    }

    @Override
    public boolean hasField(String fldname) {
        return s1.hasField(fldname) || s2.hasField(fldname);
    }

    @Override
    public void close() {
        s1.close();
        s2.close();
    }
}
