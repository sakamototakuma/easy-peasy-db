package esypsydb.materialize;

import esypsydb.query.*;
import java.util.*;


// コンパレータ
public class RecordComparator implements Comparator<Scan> {
    private List<String> fields;

    public RecordComparator(List<String> fields) {
        this.fields = fields;
    }

    /**
     * s1とs2からfieldをソートキーとして比較する
     * @param s1
     * @param s2
     * @return compareToと同様
     */
    public int compare(Scan s1, Scan s2) {
        for (String fldname : fields) {
            Constant val1 = s1.getVal(fldname);
            Constant val2 = s2.getVal(fldname);
            int result = val1.compareTo(val2);
            if (result != 0)
                return result;
        }
        return 0;   // 一致するなら0
    }

}
