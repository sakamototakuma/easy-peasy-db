package esypsydb.query;

import java.util.*;

// 指定フィールドに絞る
public class ProjectScan implements Scan {
    private Scan s;
    private Collection<String> fieldlist;

    public ProjectScan(Scan s, List<String> fieldlist) {
        this.s = s;
        this.fieldlist = fieldlist;
    }

    @Override
    public void beforeFirst() {
        s.beforeFirst();
    }

    @Override
    public boolean next() {
        return s.next();
    }

    @Override
    public int getInt(String fldname) {
        if (hasField(fldname))
            return s.getInt(fldname);
        else
            throw new RuntimeException("フィールドが見つかりません");
    }

    @Override
    public String getString(String fldname) {
        if (hasField(fldname))
            return s.getString(fldname);
        else
            throw new RuntimeException("フィールドが見つかりません");
    }

    @Override
    public Constant getVal(String fldname) {
        if (hasField(fldname))
            return s.getVal(fldname);
        else
            throw new RuntimeException("フィールドが見つかりません");
    }

    @Override
    public boolean hasField(String fldname) {
        return fieldlist.contains(fldname);
    }

    @Override
    public void close() {
        s.close();
    }
}
