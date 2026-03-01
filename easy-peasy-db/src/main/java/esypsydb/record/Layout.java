package esypsydb.record;

import static java.sql.Types.*;

import esypsydb.file.Page;
import java.util.*;

public class Layout {
    private Schema schema;
    private Map<String, Integer> offsets;
    private int slotsize;

    public Layout(Schema schema) {
        this.schema = schema;
        offsets = new HashMap<>();
        int pos = Integer.BYTES;
        for (String fldname : schema.fields()) {
            offsets.put(fldname, pos);
            pos += lengthInBytes(fldname);
        }
        slotsize = pos;
    }
    public Layout(Schema schema, Map<String, Integer> offsets, int slotsize) {
        this.schema = schema;
        this.offsets = offsets;
        this.slotsize = slotsize;
    }

    public Schema schema() {
        return schema;
    }

    public int slotSize() {
        return slotsize;
    }

    public int offset(String fldname) {
        return offsets.get(fldname);
    }

    private int lengthInBytes(String fldname) {
        int fldtype = schema.type(fldname);
        if (fldtype == INTEGER)
            return Integer.BYTES;
        else // fldtype == VARCHAR
            return Page.maxLength(schema.length(fldname));
    }
}
