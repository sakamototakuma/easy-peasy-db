package esypsydb.plan;

import java.util.List;

import esypsydb.query.ProjectScan;
import esypsydb.query.Scan;
import esypsydb.record.Schema;

public class ProjectPlan implements Plan {
    private Plan p;
    private Schema schema = new Schema();
    private List<String> fieldlist;
    
    public ProjectPlan(Plan p, List<String> fieldlist) {
        this.p = p;
        this.fieldlist = fieldlist;
        for (String fldname : fieldlist)
        schema.add(fldname, p.schema());
    }

    @Override
    public Scan open() {
        Scan s = p.open();
        return 
        new ProjectScan(s, schema.fields());
    }

    @Override
    public String nodeTypeName() {
        return "Project";
    }

    @Override
    public List<String> extraInfoLines() {
        return List.of("Output: " + String.join(", ", fieldlist));
    }

    public String accessMethod() {
        return "projection";
    }

    @Override
    public int blocksAccessed() {
        return p.blocksAccessed();
    }

    @Override
    public int recordsOutput() {
        return p.recordsOutput();
    }

    @Override
    public int distinctValues(String fldname) {
        return p.distinctValues(fldname);
    }

    @Override
    public Schema schema() {
        return schema;
    }
}
