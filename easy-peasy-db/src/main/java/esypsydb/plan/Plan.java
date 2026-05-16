package esypsydb.plan;

import esypsydb.query.Scan;
import esypsydb.record.Schema;

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
}
