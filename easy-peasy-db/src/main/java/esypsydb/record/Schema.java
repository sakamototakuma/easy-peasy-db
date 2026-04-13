package esypsydb.record;

import static java.sql.Types.*;

import java.util.*;

public class Schema {
    private List<String> fields = new ArrayList<>();
    private Map<String, FieldInfo> info = new HashMap<>();

    public void addField(String fldname, int type, int length) {
        fields.add(fldname);
        info.put(fldname, new FieldInfo(type, length));
    }

    public void addIntField(String fldname) {
        addField(fldname, INTEGER, 0);
    }

    public void addStringField(String fldname, int length) {
        addField(fldname, VARCHAR, length);
    }

    // フィールドの単一追加
    public void add(String fldname, Schema sch) {
        int type = sch.type(fldname);
        int length = sch.length(fldname);
        addField(fldname, type, length);
    }

    // スキーマ全てを追加
    public void addAll(Schema sch) {
        for (String fldname : sch.fields())
            add(fldname, sch);
    }

    public List<String> fields() {
        return fields;
    }

    public boolean hasField(String fldname) {
        return fields.contains(fldname);
    }

    public int type(String fldname) {
        return info.get(fldname).type;
    }

    public int length(String fldname) {
        return info.get(fldname).length;
    }

    private class FieldInfo {
        int type, length;
        FieldInfo(int type, int length) {
            this.type = type;
            this.length = length;
        }
    }
}
