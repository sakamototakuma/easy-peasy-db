package esypsydb.query;

import esypsydb.record.Schema;

public class Expression {
    private Constant val = null;
    private String fldname = null;

    public Expression(Constant val) {
        this.val = val;
    }

    public Expression(String fldname) {
        this.fldname = fldname;
    }

    public boolean isFieldName() {
        return fldname != null;
    }

    public Constant asConstant() {
        return val;
    }

    public String asFieldName() {
        return fldname;
    }

    // 値ならそのまま, フィールドならスキャンしてる行から列の値を抜き取る
    public Constant evaluate(Scan s) {
        return (val != null) ? val : s.getVal(fldname);
    }

    // 指定のフィールドがスキーマに含まれているかを判定
    public boolean appliesTo(Schema sch) {
        return (val != null) ? true : sch.hasField(fldname);
    }

    public String toString() {
        return (val != null) ? val.toString() : fldname;
    }
}
