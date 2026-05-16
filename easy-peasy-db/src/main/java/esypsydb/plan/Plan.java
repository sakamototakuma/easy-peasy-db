package esypsydb.plan;

import esypsydb.query.Scan;
import esypsydb.record.Schema;

import java.util.Collections;
import java.util.List;

public interface Plan {
    public Scan open();
    public int blocksAccessed();
    public int recordsOutput();
    public int distinctValues(String fldname);
    public Schema schema();

    public default String scanName() {
        String name = getClass().getSimpleName();
        if (name.endsWith("Plan"))
            return name.substring(0, name.length() - "Plan".length()) + "Scan";
        return name;
    }

    public default String accessMethod() {
        return "";
    }

    /** ノード型名 (例: "Seq Scan on student", "Filter", "Sort") */
    public default String nodeTypeName() {
        return getClass().getSimpleName().replace("Plan", "");
    }

    /** 出力タプルの推定幅（バイト） */
    public default int outputWidth() {
        int width = 0;
        for (String fld : schema().fields()) {
            int type = schema().type(fld);
            if (type == java.sql.Types.INTEGER)
                width += Integer.BYTES;
            else
                width += schema().length(fld);
        }
        return width;
    }

    /** ノード固有の追加情報行 (Filter条件, Sort Key, Group Key 等) */
    public default List<String> extraInfoLines() {
        return Collections.emptyList();
    }
}
