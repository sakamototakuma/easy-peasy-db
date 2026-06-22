package esypsydb.materialize;

import esypsydb.query.*;
import esypsydb.record.*;

import java.util.*;

// 1,2本のrunsを受け取る. 1本ならそのまま読む、2本ならMergeして読む
public class SortScan implements Scan {
    private UpdateScan s1, s2 = null, currentscan = null;
    private RecordComparator comp;
    private boolean hasmore1, hasmore2=false;
    private List<RID> savedposition;
    private boolean savedHasmore1, savedHasmore2;
    private TempTable t1, t2 = null;

    public SortScan(List<TempTable> runs, RecordComparator comp) {
        this.comp = comp;
        t1 = runs.get(0);
        s1 = (UpdateScan) t1.open();
        hasmore1 = s1.next();
        if (runs.size() > 1) {
            t2 = runs.get(1);
            s2 = (UpdateScan) t2.open();
            hasmore2 = s2.next();
        }
    }


    public void beforeFirst() {
        currentscan = null;
        s1.beforeFirst();
        hasmore1 = s1.next();
        if (s2 != null) {
            s2.beforeFirst();
            hasmore2 = s2.next();
        }
    }

    public boolean next() {
        if (currentscan != null) {
            if (currentscan == s1)
                hasmore1 = s1.next();
            else if (currentscan == s2)
                hasmore2 = s2.next();
        }

        // 両方尽きたら終了
        if (!hasmore1 && !hasmore2)
            return false;
        // 両方あるなら小さい方
        else if (hasmore1 && hasmore2) {
            if (comp.compare(s1, s2) < 0)
                currentscan = s1;
            else
                currentscan = s2;
        }
        else if (hasmore1)
            currentscan = s1;
        else if (hasmore2)
            currentscan = s2;
        return true;
    }

    public void close() {
        s1.close();
        if (s2 != null) s2.close();
        t1.close();
        if (t2 != null) t2.close();
    }
   
   public Constant getVal(String fldname) {
      return currentscan.getVal(fldname);
   }
   
   public int getInt(String fldname) {
      return currentscan.getInt(fldname);
   }
   
   public String getString(String fldname) {
      return currentscan.getString(fldname);
   }
   
   public boolean hasField(String fldname) {
      return currentscan.hasField(fldname);
   }
   
   /**
    * Saves the position of the current record,
    * so that it can be restored at a later time.
    */
   public void savePosition() {
      RID rid1 = hasmore1 ? s1.getRid() : null;
      RID rid2 = (s2 != null && hasmore2) ? s2.getRid() : null;
      savedposition = Arrays.asList(rid1, rid2);
      savedHasmore1 = hasmore1;
      savedHasmore2 = hasmore2;
   }

   // 前回保存した位置に戻す
   // currentscan を null に戻すと -> 直後の next()がどのrunも先に進めず、
   // 保存時にカレントだったレコードを再び先頭として選び直すようにする
   public void restorePosition() {
      RID rid1 = savedposition.get(0);
      RID rid2 = savedposition.get(1);
      if (rid1 != null)
         s1.moveToRid(rid1);
      if (rid2 != null)
         s2.moveToRid(rid2);
      hasmore1 = savedHasmore1;
      hasmore2 = savedHasmore2;
      currentscan = null;
   }
}
