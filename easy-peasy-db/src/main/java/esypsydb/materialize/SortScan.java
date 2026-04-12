package esypsydb.materialize;

import esypsydb.query.*;
import esypsydb.record.*;

import java.util.*;

public class SortScan implements Scan {
    private UpdateScan s1, s2 = null, currentscan = null;
    private RecordComparator comp;
    private boolean hasmore1, hasmore2=false;
    private List<RID> savedposition;


    public void beforeFirst() {

    }
}
