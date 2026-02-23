package esypsydb.query;

import java.util.*;
import esypsydb.record.Schema;

// 複数TermをANDで繋げた全体条件
public class Predicate {
    private List<Term> terms = new ArrayList<>();

    public Predicate() {}

    public Predicate(Term t) {
        terms.add(t);
    }

    // 別のpredに含まれるTermを自分のリストに追加
    public void conjoinWith(Predicate pred) {
        terms.addAll(pred.terms);
    }

    // 1つでもfalseがあればfalse
    public boolean isSatisfied(Scan s) {
        for (Term t : terms)
            if (!t.isSatisfied(s))
                return false;
        return true;
    }

    // ANDで繋がれた条件ごとの削減係数を掛ける
    // 例: A=1 (1/10に絞る) AND B=2 (1/5に絞る) なら、全体で 1/50 
    public int reductionFactor(Plan p) {
        int factor = 1;
        for (Term t : terms)
            factor *= t.reductionFactor(p);
        return factor;
    }

    /**
     * 指定されたスキーマの列だけ使ってる条件を抽出
     * プッシュダウン最適化：JOIN前に、各テーブルに適用できるフィルタを先にかける
     * @param sch schema
     * @return pred or null   例) Predicate([major = 'CS'])
    */
    public Predicate selectSubPred(Schema sch) {
        Predicate result = new Predicate();
        for (Term t : terms)
            if (t.appliesTo(sch))
                result.terms.add(t);
            if (result.terms.size() == 0)
                return null;
        else
            return result;
    }

    /**
     * 結合条件を抽出
     * 
     * @param sch1, sch2
     * @return  pred or null  例)redicate([STUDENT.sid = ENROLL.sid])
    */ 
    public Predicate joinSubPred(Schema sch1, Schema sch2) {
        Predicate result = new Predicate();
        Schema newsch = new Schema();
        newsch.addAll(sch1);
        newsch.addAll(sch2);
        for (Term t : terms)
            if (!t.appliesTo(sch1) &&
                !t.appliesTo(sch2) &&
                t.appliesTo(newsch))
            result.terms.add(t);
        if (result.terms.size() == 0)
            return null;
        else
            return result;
    }

    // 比較する定数を返す
    // インデックス検索（指定した値で直接検索）ができるかどうかの判断に使う
    public Constant equatesWithConstant(String fldname) {
      for (Term t : terms) {
         Constant c = t.equatesWithConstant(fldname);
         if (c != null)
            return c;
      }
      return null;
   }

   public String equatesWithField(String fldname) {
      for (Term t : terms) {
         String s = t.equatesWithField(fldname);
         if (s != null)
            return s;
      }
      return null;
   }

   public String toString() {
    Iterator<Term> iter = terms.iterator();
    if (!iter.hasNext()) 
        return "";
    String result = iter.next().toString();
    while (iter.hasNext()) {
        result += " and " + iter.next().toString();
    }
    return result;
   }
}
