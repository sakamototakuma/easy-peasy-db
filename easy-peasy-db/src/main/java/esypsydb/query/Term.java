package esypsydb.query;

import esypsydb.record.Schema;;

// 項（Term）は2つの式の比較
public class Term {
    private Expression lhs, rhs;

    /**
     * 2つの式を等値比較する新しい項を生成
     * @param lhs 左辺の式
     * @param rhs 右辺の式
     */
    public Term(Expression lhs, Expression rhs) {
        this.lhs = lhs;
        this.rhs = rhs;
    }

    /**
     * 指定されたスキャンにおいて、項の両辺の式が同じ定数に評価される場合にtrue
     * @param s スキャン
     * @return 両辺の式がスキャン上で同じ値を持つ場合 true
     */
    public boolean isSatisfied(Scan s) {
        Constant lhsval = lhs.evaluate(s);
        Constant rhsval = rhs.evaluate(s);
        return rhsval.equals(lhsval);
    }

    // 項の両辺の式が指定されたスキャンに適用できる場合に true を返す
    public boolean appliesTo(Schema sch) {
        return lhs.appliesTo(sch) && rhs.appliesTo(sch);
    }

    /**
     * この項を選択条件として使った場合に、クエリの出力レコード数がどれだけ削減されるかを計算する
     * たとえば削減係数が2であれば、出力が半分になることを意味する。
     * @param p クエリのプラン
     * @return 整数の削減係数
     */
    public int reductionFactor(Plan p) {
        String lhsName, rhsName;
        if (lhs.isFieldName() && rhs.isFieldName()) {
            lhsName = lhs.asFieldName();
            rhsName = rhs.asFieldName();
            return Math.max(p.distinctValues(lhsName),
                            p.distinctValues(rhsName));
        }
        if (lhs.isFieldName()) {
            lhsName = lhs.asFieldName();
            return p.distinctValues(lhsName);
        }
        if (rhs.isFieldName()) {
            rhsName = rhs.asFieldName();
            return p.distinctValues(rhsName);
        }
        // それ以外の場合、項は定数同士の等値比較
        if (lhs.asConstant().equals(rhs.asConstant()))
            return 1;
        else
            return Integer.MAX_VALUE;
    }

    /**
     * この項が "F=c" の形（F は指定フィールド、c はある定数）であるか判定する。
     * そうであれば定数 c を返し、そうでなければ null を返す。
     * @param fldname フィールド名
     * @return 定数、または null
     */
    public Constant equatesWithConstant(String fldname) {
        if (lhs.isFieldName() &&
            lhs.asFieldName().equals(fldname) &&
            !rhs.isFieldName())
            return rhs.asConstant();
        else if (rhs.isFieldName() &&
                 rhs.asFieldName().equals(fldname) &&
                 !lhs.isFieldName())
            return lhs.asConstant();
        else
            return null;
    }

    /**
     * この項が "F1=F2" の形（F1 は指定フィールド、F2 は別のフィールド）であるか判定する。
     * そうであれば F2 のフィールド名を返し、そうでなければ null を返す。
     * @param fldname フィールド名
     * @return もう一方のフィールド名、または null
     */
    public String equatesWithField(String fldname) {
      if (lhs.isFieldName() &&
          lhs.asFieldName().equals(fldname) &&
          rhs.isFieldName())
         return rhs.asFieldName();
      else if (rhs.isFieldName() &&
               rhs.asFieldName().equals(fldname) &&
               lhs.isFieldName())
         return lhs.asFieldName();
      else
         return null;
   }

   public String toString() {
      return lhs.toString() + "=" + rhs.toString();
   }
}
