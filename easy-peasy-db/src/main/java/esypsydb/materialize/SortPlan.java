package esypsydb.materialize;

import esypsydb.record.*;
import esypsydb.plan.Plan;
import esypsydb.query.*;
import esypsydb.tx.Transaction;
import java.util.*;

public class SortPlan implements Plan {
    private Plan p;
    private Transaction tx;
    private Schema sch;
    private RecordComparator comp;
    private List<String> sortfields;

    public SortPlan(Plan p, List<String> sortfields, Transaction tx) {
        this.p = p;
        this.tx = tx;
        this.sortfields = sortfields;
        sch = p.schema();
        comp = new RecordComparator(sortfields);
    }

    /**
     * 
     */
    public Scan open() {
        Scan src = p.open();
        List<TempTable> runs = splitIntoRuns(src);
        src.close();
        while (runs.size() > 2)
            runs = doMergeIteration(runs);
        return new SortScan(runs, comp);
    }

    @Override
    public String nodeTypeName() {
        return "Sort";
    }

    @Override
    public List<String> extraInfoLines() {
        return List.of("Sort Key: " + String.join(", ", sortfields));
    }

    public String accessMethod() {
        return "external-sort";
    }

    public int blocksAccessed() {
        Plan mp = new MaterializePlan(tx, p);
        return mp.blocksAccessed();
    }

    // ソートしても行数は普遍
    public int recordsOutput() {
        return p.recordsOutput();
    }

    public Schema schema() {
        return sch;
    }

    public int distinctValues(String fieldname) {
        return p.distinctValues(fieldname);
    }

    /**
     * tempsはrun list
     * 入力を先頭レコードに移す。次のレコードがないなら空のrun listを返す
     * whileは入ったら
     * 1. srcのcurrent record = 次の入力レコード
     * 2. currentscanのcurrent record = runの最後に追加したレコード
     * ifは
     * -> 今のrunの最後のレコードが次の入力レコードより小さいなら
     * 昇順が崩れるから、区切ってtempsに追加
     * 新しい
     * 
     * @param src
     * @return temps: 分割したruns
     */
    public List<TempTable> splitIntoRuns(Scan src) {
        List<TempTable> temps = new ArrayList<TempTable>();
        src.beforeFirst();
        if (!src.next())
            return temps;
            
        final int MAX_RECORDS = 10000;
        boolean hasmore = true;
        
        while (hasmore) {
            TempTable currenttemp = new TempTable(tx, sch);
            temps.add(currenttemp);
            UpdateScan currentscan = (UpdateScan) currenttemp.open();
            
            List<Map<String, Constant>> chunk = new ArrayList<Map<String, Constant>>();
            int count = 0;
            while (hasmore && count < MAX_RECORDS) {
                Map<String, Constant> rec = new HashMap<String, Constant>();
                for (String fldname : sch.fields())
                    rec.put(fldname, src.getVal(fldname));
                chunk.add(rec);
                count++;
                hasmore = src.next();
            }
            
            chunk.sort((r1, r2) -> {
                for (String fldname : sortfields) {
                    Constant val1 = r1.get(fldname);
                    Constant val2 = r2.get(fldname);
                    int result = val1.compareTo(val2);
                    if (result != 0)
                        return result;
                }
                return 0;
            });
            
            for (Map<String, Constant> rec : chunk) {
                currentscan.insert();
                for (String fldname : sch.fields())
                    currentscan.setVal(fldname, rec.get(fldname));
            }
            currentscan.close();
        }
        
        return temps;
    }

    /**
     * 現在のrunsから先頭2つを取得してマージ
     * 
     * @param runs
     * @return
     */
    private List<TempTable> doMergeIteration(List<TempTable> runs) {
        List<TempTable> result = new ArrayList<TempTable>();
        while (runs.size() > 1) {
            TempTable p1 = runs.remove(0);
            TempTable p2 = runs.remove(0);
            result.add(mergeTwoRuns(p1, p2));
            p1.close();
            p2.close();
        }
        if (runs.size() == 1)
            result.add(runs.get(0));
        return result;
    }

    private TempTable mergeTwoRuns(TempTable p1, TempTable p2) {
        Scan src1 = p1.open();
        Scan src2 = p2.open();
        TempTable result = new TempTable(tx, sch);
        UpdateScan dest = result.open();

        boolean hasmore1 = src1.next();
        boolean hasmore2 = src2.next();
        // どちらかが無くなるまで（より大きい値を持つ方が残る）
        while (hasmore1 && hasmore2)
            // 小さい方の値をdestに書き込んで、next()する
            if (comp.compare(src1, src2) < 0)
                hasmore1 = copy(src1, dest);
            else
                hasmore2 = copy(src2, dest);
        
        // hasmore2だけがなくなったら、hasmore1を全部書き写す
        if (hasmore1)
            while (hasmore1)
                hasmore1 = copy(src1, dest);
        // hasmore1だけがなくなったら、hasmore2を全部書き写す
        else
            while (hasmore2)
            hasmore2 = copy(src2, dest);

        src1.close();
        src2.close();
        dest.close();
        return result;
    }
    
    /**
     * srcの内容をcurrentscanに書き写すメソッド
     * 1. srcのcurrent recordをcurrentscan(dest)に書く
     * 2. src.next()して次の入力へ
     * 3. そのnext()の有無をbooleanで返す
     * 
     * @param src
     * @param dest
     * @return srcのcurrent recordが次にあるならtrue
     */
    private boolean copy(Scan src, UpdateScan dest) {
        dest.insert();
        for (String fldname : sch.fields())
            dest.setVal(fldname, src.getVal(fldname));
        return src.next();
    }
}
