package esypsydb.materialize;

import esypsydb.query.*;

import java.util.*;

public class GroupValue {
    private Map<String, Constant> vals;

    /**
     * fldnameをキーとして、SortScanをvals: Mapにput
     * @param s
     * @param fields
     */
    public GroupValue(Scan s, Collection<String> fields) {
        vals = new HashMap<String, Constant>();
        for (String fldname : fields)
            vals.put(fldname, s.getVal(fldname));
    }

    public Constant getVal(String fldname) {
        return vals.get(fldname);
    }

    @Override
    public boolean equals(Object obj) {
        GroupValue gv = (GroupValue) obj;
        for (String fldname : vals.keySet()) {
            Constant v1 = vals.get(fldname);
            Constant v2 = gv.getVal(fldname);
            if (!v1.equals(v2))
                return false;
        }
        return true;
    }

    public int hashCode() {
        int hashval = 0;
        for (Constant c : vals.values())
            hashval += c.hashCode();
        return hashval;
    }
}
